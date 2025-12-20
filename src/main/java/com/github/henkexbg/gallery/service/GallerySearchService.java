package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.bean.*;
import com.github.henkexbg.gallery.job.GalleryRootDirChangeListener;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;
import com.github.henkexbg.gallery.util.GalleryFileUtils;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.github.henkexbg.gallery.util.GalleryFileUtils.*;
import static org.apache.commons.io.FileUtils.listFilesAndDirs;

@Service
public class GallerySearchService implements GalleryRootDirChangeListener {

    public static final int MAX_PAGE_SIZE = 2000;

    final Logger LOG = LoggerFactory.getLogger(getClass());
    final Map<String, String> ISO_COUNTRY_NAME_MAP;

    @Resource
    GalleryAuthorizationService galleryAuthorizationService;

    @Resource
    GalleryService galleryService;

    @Resource
    FilenameToSearchTermsStrategy filenameToSearchTermsStrategy;

    @Resource
    MetadataExtractionService metadataExtractionService;

    @Resource
    Jdbi jdbi;

    @Resource(name = "virtualThreadExecutorService")
    ExecutorService executorService;

    DirectoryWatcher directoryWatcher = null;

    Deduplicator deduplicator;

    public GallerySearchService() {
        ISO_COUNTRY_NAME_MAP = new HashMap<>();
        String[] isoCountries = Locale.getISOCountries();
        for (String country : isoCountries) {
            Locale locale = new Locale("en", country);
            ISO_COUNTRY_NAME_MAP.put(locale.getCountry(), locale.getDisplayCountry());
        }
    }

    @PostConstruct
    public void init() {
        LOG.info("Creating Deduplicator");
        deduplicator = new Deduplicator();
        deduplicator.addListener((paths) -> {
            Set<File> files = paths.stream().map(Path::toFile).collect(Collectors.toCollection(HashSet::new));
            onFilesUpdated(files, Set.of());
        });
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Shutting down GallerySearchService");
        if (deduplicator != null) {
            deduplicator.stop();
        }
    }

    public SearchResult search(SearchQuery searchQuery) throws IOException, NotAllowedException {
        String publicPath = searchQuery.publicPath();
        List<String> basePathSearchTerms = new ArrayList<>();
        if (StringUtils.isNotBlank(publicPath)) {
            basePathSearchTerms.add(galleryAuthorizationService.getRealFileOrDir(publicPath).getCanonicalPath() + "_%");
        } else {
            galleryAuthorizationService.getRootPathsForCurrentUser().forEach((k, v) -> {
                try {
                    basePathSearchTerms.add(v.getCanonicalPath() + "_%");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        String searchTerm = searchQuery.searchTerm();
        LOG.debug("Performing search with publicPath={}, basePaths={} searchTerm={}", publicPath, basePathSearchTerms, searchTerm);

        List<String> searchTerms = searchTerm != null ?
                Arrays.stream(searchTerm.split("\\s")).map(String::trim).map(String::toLowerCase).map(s -> s + "%").toList() :
                Collections.emptyList();

        // Not pretty but we need to build a prepared statement with a dynamic number of paths and search terms
        StringBuilder sb = new StringBuilder("SELECT * FROM GALLERY_FILE f WHERE (");
        for (int i = 0; i < basePathSearchTerms.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("f.PATH_ON_DISK LIKE :path%s".formatted(i));
        }
        sb.append(")");
        if (!searchTerms.isEmpty()) {
            sb.append(" AND f.ID IN (SELECT DISTINCT t.FILE_ID FROM TAG t WHERE ");
            for (int i = 0; i < searchTerms.size(); i++) {
                if (i > 0) sb.append(" OR ");
                sb.append("t.TEXT LIKE :term%s".formatted(i));
            }
            sb.append(")");
        }
        sb.append(" ORDER BY f.date_taken DESC");
        // Add pagination
        int startPage = Math.max(0, searchQuery.startPage());
        int pageSize = searchQuery.pageSize() <= 0 ? MAX_PAGE_SIZE : searchQuery.pageSize();
        int offset = Math.max(0, startPage * pageSize);
        sb.append(" LIMIT :limit OFFSET :offset");

        String fullQuery = sb.toString();
        List<DbFile> list;
        long startQueryTime = System.currentTimeMillis();
        try {
            list = jdbi.withHandle(handle -> {
                Query query = handle.createQuery(fullQuery);
                for (int i = 0; i < basePathSearchTerms.size(); i++) {
                    query.bind("path%s".formatted(i), basePathSearchTerms.get(i));
                }
                for (int i = 0; i < searchTerms.size(); i++) {
                    query.bind("term%s".formatted(i), searchTerms.get(i));
                }
                query.bind("limit", pageSize);
                query.bind("offset", offset);
                return query.mapTo(DbFile.class).stream().toList();
            });
        } catch (Exception e) {
            LOG.error("Error when performing database search", e);
            throw new IOException(e);
        }
        LOG.debug("Performing database search took {}ms (page={}, pageSize={}, offset={})", System.currentTimeMillis() - startQueryTime, startPage, pageSize, offset);
        List<GalleryFile> galleryFiles =
                list.stream().filter(df -> !df.getIsDirectory()).map(this::createGalleryFileFromDbFile).filter(Objects::nonNull).toList();
        List<GalleryDirectory> galleryDirectories =
                list.stream().filter(DbFile::getIsDirectory).map(this::createGalleryDirectoryFromDbFile).filter(Objects::nonNull).toList();
        LOG.debug("Returning {} directories and {} gallery files", galleryDirectories.size(), galleryFiles.size());
        return new SearchResult(galleryDirectories, galleryFiles);
    }

    @Override
    public synchronized void onGalleryRootDirsUpdated(Collection<GalleryRootDir> galleryRootDirs) {
        LOG.debug("onGalleryRootDirsUpdated(galleryRootDirs: {}", galleryRootDirs);
        List<Path> rootDirs = galleryRootDirs.stream().map(grd -> grd.getDir().toPath()).toList();
        LOG.debug("Found {} directories to watch for search service", rootDirs.size());

        try {
            if (directoryWatcher != null) {
                directoryWatcher.close();
            }
            directoryWatcher = DirectoryWatcher.builder().paths(rootDirs) // or use paths(directoriesToWatch)
                    .listener(event -> {
                        switch (event.eventType()) {
                            case CREATE, MODIFY:
                                // Run via deduplicator to remove duplicates. Not required for DELETE
                                deduplicator.add(event.path());
                                break;
                            case DELETE:
                                onFilesUpdated(Collections.emptySet(), Set.of(event.path().toFile()));
                                break;
                        }
                    }).fileHashing(false)
                    .build();
            LOG.debug("Built directory watcher for search service. About to start watching");
            directoryWatcher.watchAsync();
        } catch (IOException ioe) {
            LOG.error("Exception while reading gallery root directories for DB indexing", ioe);
        }
        LOG.debug("Done adding directory watcher for search service");

    }

    /**
     * Goes through all directories and files under all root paths configured, and triggers a DB update for each. The DB will not update
     * records that haven't changed according to modification time.
     */
    public void createOrUpdateAllDirectories() {
        try {
            Collection<File> rootDirectories = getRootDirectoriesForCurrentUser();
            Collection<File> allDirectoriesCol = getAllDirectories(rootDirectories);
            List<File> allDirectoriesSorted =
                    allDirectoriesCol.stream().sorted(Comparator.comparingInt(f -> getPathName(f).length())).toList();
            for (File oneDirectory : allDirectoriesSorted) {
                try {
                    createOrUpdateOneDirectory(oneDirectory, rootDirectories);
                } catch (IOException e) {
                    LOG.error("Error while creating or updating directory {} in database", oneDirectory, e);
                }
                List<File> filesInDir = Arrays.stream(Objects.requireNonNull(oneDirectory.listFiles())).filter(File::isFile).toList();
                List<CompletableFuture<Void>> updatedFileFutures = filesInDir.stream().map(f -> CompletableFuture.runAsync(() -> {
                    try {
                        createOrUpdateOneFile(f);
                    } catch (Exception e) {
                        LOG.error("Failed in updating {}. Ignoring", f, e);
                    }
                }, executorService)).toList();
                CompletableFuture.allOf(updatedFileFutures.toArray(new CompletableFuture[0])).join();
            }
        } catch (IOException | NotAllowedException e) {
            LOG.error("Error while creating or updating directories and files in database", e);
        }
    }

    /**
     * Finds all files in the DB of type video
     *
     * @return A list of files
     */
    public List<File> findAllVideos() {
        final String findOneQuery = """
                SELECT * FROM PUBLIC.gallery_file WHERE file_type = 'VIDEO'
                """;
        List<DbFile> videoDbFiles = jdbi.withHandle(handle ->
                handle.createQuery(findOneQuery).mapTo(DbFile.class).stream().toList()
        );
        return videoDbFiles.stream().map(dbFile -> new File(dbFile.getPathOnDisk())).toList();
    }

    /**
     * This is called when any files and directories are modified or deleted within the root directories. The job here is to update the
     * database appropriately
     *
     * @param upsertedFiles Created or updated filed
     * @param deletedFiles  Deleted files
     */
    void onFilesUpdated(Set<File> upsertedFiles, Set<File> deletedFiles) {
        LOG.debug("onFilesUpdated(createdFiles: {}, deletedFiles: {}", upsertedFiles, deletedFiles);
        try {
            galleryAuthorizationService.loginAdminUser();
            Collection<File> rootDirectories = getRootDirectoriesForCurrentUser();
            List<File> allUpdatedFilesSorted = upsertedFiles.stream().sorted(GalleryFileUtils.shortestPathComparatorFile()).toList();
            allUpdatedFilesSorted.forEach(f -> {
                try {
                    if (f.isDirectory()) {
                        createOrUpdateOneDirectory(f, rootDirectories);
                    } else {
                        createOrUpdateOneFile(f);
                    }
                } catch (IOException e) {
                    LOG.error("Error when updating {}. Ignoring", f, e);
                }
            });
            List<File> allDeletedFilesSorted = deletedFiles.stream().sorted(GalleryFileUtils.shortestPathComparatorFile()).toList();
            allDeletedFilesSorted.forEach(df -> {
                try {
                    deleteOneFile(df);
                } catch (IOException e) {
                    LOG.error("Error when deleting {}. Ignoring", df, e);
                }
            });
        } catch (Exception e) {
            LOG.error("Error when updating files {} or deleting files {}. Aborting", upsertedFiles, deletedFiles, e);
        } finally {
            galleryAuthorizationService.logoutAdminUser();
        }
    }

    GalleryFile createGalleryFileFromDbFile(DbFile dbFile) {
        try {
            String path = dbFile.getPathOnDisk();
            File realFile = new File(path);
            String publicPath = galleryService.getPublicPathFromRealFile(realFile);
            GalleryFile galleryFile = galleryService.createGalleryFile(publicPath, realFile);
            if (dbFile.getDateTaken() != null) {
                galleryFile.setDateTaken(dbFile.getDateTaken());
            }
            return galleryFile;
        } catch (NotAllowedException nae) {
            LOG.error("Not allowed to access search result file. Skipping file.", nae);
            return null;
        } catch (IOException ioe) {
            LOG.error("IOException when accessing search result file. Skipping file", ioe);
            return null;
        }
    }

    GalleryDirectory createGalleryDirectoryFromDbFile(DbFile dbFile) {
        try {
            String path = dbFile.getPathOnDisk();
            File realFile = new File(path);
            String publicPath = galleryService.getPublicPathFromRealFile(realFile);
            return galleryService.createGalleryDirectory(publicPath, realFile);
        } catch (NotAllowedException nae) {
            LOG.error("Not allowed to access search result file. Skipping file.", nae);
            return null;
        } catch (IOException ioe) {
            LOG.error("IOException when accessing search result file. Skipping file", ioe);
            return null;
        }
    }

    void createOrUpdateOneDirectory(File directory, Collection<File> rootDirectories) throws IOException {
        final String findParentQuery = """
                SELECT id FROM gallery_file
                WHERE path_on_disk = :path_on_disk
                """;
        final String mergeQueryChildDir = """
                MERGE INTO gallery_file (parent_id, path_on_disk, is_directory, last_modified)
                KEY (path_on_disk)
                VALUES (:parent_id, :path_on_disk, true, :last_modified)
                """;
        try {
            if (isDbUpToDate(directory)) {
                LOG.debug("Skipping update of {} as it doesn't need to be updated", directory);
                return;
            }
            List<String> filenameParts = filenameToSearchTermsStrategy.generateSearchTermsFromFilename(directory);
            String directoryPath = directory.getCanonicalPath();
            String parentPath = rootDirectories.contains(directory) ? null : directory.getParentFile().getCanonicalPath();
            AtomicLong atomicDirectoryPk = new AtomicLong();
            jdbi.useHandle(handle -> {
                Update updateQueryObj = handle.createUpdate(mergeQueryChildDir).bind("path_on_disk", directoryPath)
                        .bind("last_modified", new Timestamp(directory.lastModified()));

                if (parentPath != null) {
                    Integer parentId = handle.createQuery(findParentQuery).bind("path_on_disk", parentPath).mapTo(Integer.class).one();
                    updateQueryObj.bind("parent_id", parentId);
                } else {
                    updateQueryObj.bindNull("parent_id", Types.BIGINT);
                }

                long directoryPk = updateQueryObj.executeAndReturnGeneratedKeys().mapTo(Long.class).one();
                atomicDirectoryPk.set(directoryPk);
            });
            updateFilenameTags(directory, atomicDirectoryPk.get());
        } catch (Exception e) {
            LOG.error("Error while creating or updating directory {} in database", directory, e);
            throw new IOException(e);
        }
    }

    boolean isDbUpToDate(File file) throws IOException {
        final String findOneQuery = """
                SELECT * FROM PUBLIC.gallery_file WHERE path_on_disk = :path_on_disk
                """;
        return jdbi.withHandle(handle -> {
            Optional<DbFile> dbFile =
                    handle.createQuery(findOneQuery).bind("path_on_disk", file.getCanonicalPath()).mapTo(DbFile.class).stream().findAny();
            return dbFile.isPresent() && dbFile.get().getLastModified() != null &&
                    dbFile.get().getLastModified().toEpochMilli() >= file.lastModified();
        });
    }

    void deleteOneFile(File file) throws IOException {
        final String deleteGalleryFileQuery = """
                DELETE FROM PUBLIC.gallery_file WHERE path_on_disk = :path_on_disk
                """;
        jdbi.useHandle(handle -> {
            String filePath = file.getCanonicalPath();
            int nrDeleted = handle.createUpdate(deleteGalleryFileQuery).bind("path_on_disk", filePath).execute();
            LOG.debug("Deleting {} resulted in {} rows removed in DB", filePath, nrDeleted);
        });
    }

    void createOrUpdateOneFile(File file) throws IOException {
        try {
            if (!galleryService.isAllowedExtension(file)) {
                return;
            }
            if (isDbUpToDate(file)) {
                LOG.debug("Skipping update of {} as it doesn't need to be updated", file);
                return;
            }
            final String findParentQuery = """
                    SELECT id FROM gallery_file
                    WHERE path_on_disk = :path_on_disk
                    """;
            final String mergeQuery = """
                    MERGE INTO gallery_file (parent_id, path_on_disk, is_directory, file_type, content_type, location, date_taken, last_modified)
                    KEY (path_on_disk)
                    VALUES (:parent_id, :path_on_disk, false, :file_type, :content_type, :location, :date_taken, :last_modified)
                    """;
            String parentPath = file.getParentFile().getCanonicalPath();
            MetadataExtractionService.FileMetaData metadata = metadataExtractionService.getMetadata(file);
            String point = metadata.gpsLatitude() != null && metadata.gpsLongitude() != null ?
                    "POINT(%s %s)".formatted(metadata.gpsLongitude(), metadata.gpsLatitude()) : null;
            String contentType = getContentType(file);
            AtomicLong atomicFileId = new AtomicLong();

            jdbi.useHandle(handle -> {
                Integer parentId = handle.createQuery(findParentQuery).bind("path_on_disk", parentPath).mapTo(Integer.class).one();
                if (parentId == null) {
                    throw new IOException("File %s did not have a valid parent directory".formatted(file.getCanonicalPath()));
                }
                Long fileId = handle.createUpdate(mergeQuery).bind("parent_id", parentId).bind("path_on_disk", file.getCanonicalPath())
                        .bind("file_type", isVideo(file) ? "video" : "image").bind("content_type", contentType)
                        .bind("location", point)
                        .bind("date_taken", metadata.dateTaken() != null ? new Timestamp(metadata.dateTaken().toEpochMilli()) : null)
                        .bind("last_modified", new Timestamp(file.lastModified())).executeAndReturnGeneratedKeys().mapTo(Long.class).one();

                atomicFileId.set(fileId);
            });
            updateFilenameTags(file, atomicFileId.get());
            if (metadata.gpsLatitude() != null &&  metadata.gpsLongitude() != null) {
                List<Location> nearestLocations = getNearestLocations(metadata.gpsLongitude(), metadata.gpsLatitude());
                updateLocationTags(file, atomicFileId.get(), nearestLocations);
            }

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    List<Location> getNearestLocations(double lon, double lat) {
        final List<Double> intervals = List.of(0.1d, 1d, 10d);
        final String query = """
                WITH candidates AS (
                    SELECT id, the_geom, name, country_iso_a2, feature_code
                    FROM location
                    WHERE the_geom && ST_Envelope(
                        ST_Buffer(
                            ST_GeomFromText(:location, 4326),
                            :interval
                        )
                    )
                )
                SELECT id, the_geom, name, country_iso_a2, feature_code
                FROM candidates
                ORDER BY ST_Distance(the_geom, ST_GeomFromText(:location, 4326))
                LIMIT 5;
                """;
        String point = "POINT(%s %s)".formatted(lon, lat);
        List<Location> locations = null;
        for (Double interval : intervals) {
            locations = jdbi.withHandle(handle ->
                    handle.createQuery(query).bind("location", point).bind("interval", interval).mapTo(Location.class).list()
            );
            if (!locations.isEmpty()) {
                LOG.debug("Found {} nearest locations for interval {}", locations.size(), interval);
                break;
            }
        }
        return locations.stream().collect(Collectors.groupingBy(Location::getFeatureCode)).values().stream().map(List::getFirst).toList();
    }

    void updateFilenameTags(File fileOrDir, long fileId) throws IOException {
        List<String> filenameParts = filenameToSearchTermsStrategy.generateSearchTermsFromFilename(fileOrDir);
        updateTagsForSource(fileOrDir, fileId, "FILENAME", filenameParts);
    }

    void updateLocationTags(File fileOrDir, long fileId, List<Location> locations) throws IOException {
        Set<String> locationParts = new HashSet<>();
        for (Location location : locations) {
            String countryName = ISO_COUNTRY_NAME_MAP.get(location.getCountryIsoA2());
            if (countryName == null) {
                LOG.warn("ISO code {} is not a valid country. Ignoring", location.getCountryIsoA2());
                continue;
            }
            locationParts.add(location.getName());
            locationParts.add(countryName);
            locationParts.add(location.getCountryIsoA2());
            locationParts.add(location.getFeatureCode());
        }
        updateTagsForSource(fileOrDir, fileId, "LOCATION", locationParts);
    }

    void updateTagsForSource(File fileOrDir, long fileId, String source, Collection<String> newTags) throws IOException {
        final String deleteTagsQuery = """
                DELETE FROM tag WHERE source = :source AND file_id = :file_id
                """;
        final String updateTagsQuery = """
                INSERT INTO tag (file_id, source, text) VALUES (:file_id, :source, :text)
                """;
        try {
            jdbi.useTransaction(handle -> {
                handle.createUpdate(deleteTagsQuery).bind("file_id", fileId).bind("source", source).execute();
                for (String newTag : newTags) {
                    if (StringUtils.isNotBlank(newTag)) {
                        handle.createUpdate(updateTagsQuery).bind("file_id", fileId).bind("source", source).bind("text", newTag).execute();
                    }
                }
            });
        } catch (Exception e) {
            LOG.error("Error while creating or updating tags for file {} with ID {} in database", fileOrDir, fileId, e);
            throw new IOException(e);
        }
    }

    Collection<File> getRootDirectoriesForCurrentUser() throws IOException {
        Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
        return rootPathsForCurrentUser.values();
    }

    Collection<File> getAllDirectories(Collection<File> dirs) throws IOException, NotAllowedException {
        Collection<File> allDirectories = new HashSet<>();
        dirs.forEach(dir -> allDirectories.addAll(
                listFilesAndDirs(dir, FileFilterUtils.falseFileFilter(), FileFilterUtils.directoryFileFilter())));
        LOG.debug("Returning {} directories", allDirectories.size());
        return allDirectories;
    }

    public record SearchQuery(String publicPath, String searchTerm, int startPage, int pageSize) {}
}

