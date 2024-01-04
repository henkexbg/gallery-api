package com.github.henkexbg.gallery.service.impl;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDBException;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.util.MapBuilder;
import com.github.henkexbg.gallery.bean.GalleryRootDir;
import com.github.henkexbg.gallery.bean.LocationResult;
import com.github.henkexbg.gallery.job.GalleryRootDirChangeListener;
import com.github.henkexbg.gallery.service.*;
import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.listFilesAndDirs;

public class GallerySearchServiceImpl implements GallerySearchService, GalleryRootDirChangeListener {

    private enum VertexType {
        FILE,
        DIR
    }

    private static final String COLLECTION_NAME_GALLERY_IMAGES = "galleryImages";

    private static final String GRAPH_NAME = "galleryImageOwns";

    private static final String ROOT_NODE_NAME = "ROOT_NODE";

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private ArangoDatabase db;

    private GalleryAuthorizationService galleryAuthorizationService;

    private GalleryService galleryService;

    private FilenameToSearchTermsStrategy filenameToSearchTermsStrategy;

    private MetadataExtractionService metadataExtractionService;

    private LocationMetadataService locationMetadataService;

    private String dbHost;

    private Integer dbPort;

    private String dbName = "_system";

    private String dbUsername;

    private String dbPassword;

    public void setGalleryAuthorizationService(GalleryAuthorizationService galleryAuthorizationService) {
        this.galleryAuthorizationService = galleryAuthorizationService;
    }

    public void setGalleryService(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    public void setFilenameToSearchTermsStrategy(FilenameToSearchTermsStrategy filenameToSearchTermsStrategy) {
        this.filenameToSearchTermsStrategy = filenameToSearchTermsStrategy;
    }

    public void setMetadataExtractionService(MetadataExtractionService metadataExtractionService) {
        this.metadataExtractionService = metadataExtractionService;
    }

    public void setLocationMetadataService(LocationMetadataService locationMetadataService) {
        this.locationMetadataService = locationMetadataService;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public void setDbPort(Integer dbPort) {
        this.dbPort = dbPort;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    @PostConstruct
    public void init() {
        ArangoDB.Builder arangoDBBuilder = new ArangoDB.Builder().user(dbUsername).password(dbPassword);
        if (StringUtils.isNotBlank(dbHost) && dbPort > 0) {
            arangoDBBuilder.host(dbHost, dbPort);
        }
        db = arangoDBBuilder.build().db(dbName);

        // Create the root node if not existing
        final String query =
                "INSERT {_key: @key} IN " + COLLECTION_NAME_GALLERY_IMAGES + " OPTIONS { ignoreErrors: true }";
        Map<String, Object> bindVars = new MapBuilder()
                .put("key", ROOT_NODE_NAME)
                .get();
        db.query(query, bindVars, null, BaseDocument.class);
    }

    @Override
    public List<GalleryFile> search(String publicPath, String searchTerm) throws IOException, NotAllowedException {
        List<String> startNodes = new ArrayList<>();
        if (StringUtils.isNotBlank(publicPath)) {
            startNodes.add(COLLECTION_NAME_GALLERY_IMAGES + "/" + getKey(galleryAuthorizationService.getRealFileOrDir(publicPath)));
        } else {
            List<File> rootDirsForUser = galleryAuthorizationService.getRootPathsForCurrentUser().entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());
            for (File f : rootDirsForUser) {
                startNodes.add(COLLECTION_NAME_GALLERY_IMAGES + "/" + getKey(f));
            }
        }
        LOG.debug("Performing search with publicPath={}, startNodes={} searchTerm={}", publicPath, startNodes, searchTerm);

        final String query = """
                FOR startNode in @startNodes
                  FOR v, e, p IN 1..5 OUTBOUND startNode galleryImageOwns
                    FILTER v.type == 'FILE'
                    LET a1 = APPEND(FLATTEN(p.vertices[*].tags), v.tags)
                    LET a2 = APPEND(a1, APPEND(FLATTEN(p.vertices[*].filenameParts), v.fileNameParts))
                    LET a3 = APPEND(a2, FLATTEN(p.vertices[*].physicalLocations), v.physicalLocations)
                    LET a4 = APPEND(a3, v.country)
                    LET actualSearchArray = (FOR elem IN a4 RETURN LOWER(elem))
                    LET lowerCaseSearchParams = (FOR elem IN @searchTerms RETURN LOWER(elem))
                    FILTER lowerCaseSearchParams ALL IN actualSearchArray
                    SORT v.dateTaken DESC
                    RETURN v
                """;

        LOG.debug("Final query: {}", query);
        Map<String, Object> bindVars = new MapBuilder().put("startNodes", startNodes).put("searchTerms", Arrays.stream(searchTerm.split(",")).map(s -> s.trim()).toList()).get();
        ArangoCursor<GalleryDocument> cursor = db.query(query, bindVars, null, GalleryDocument.class);
        List<GalleryFile> galleryFiles = new ArrayList<>();
        cursor.forEachRemaining(aDocument -> {
            GalleryFile oneGalleryFile = createGalleryFileFromSearchResult(aDocument);
            if (oneGalleryFile != null) {
                galleryFiles.add(oneGalleryFile);
            }
        });
        LOG.debug("Returning {} gallery files", galleryFiles.size());
        return galleryFiles;
    }


    private DirectoryWatcher directoryWatcher;

    @Override
    public void onGalleryRootDirsUpdated(Collection<GalleryRootDir> galleryRootDirs) {
        LOG.debug("onGalleryRootDirsUpdated(galleryRootDirs: {}", galleryRootDirs);
        List<Path> rootDirs = galleryRootDirs.stream().map(grd -> grd.getDir().toPath()).toList();
//        updateRootDirectories(rootDirs);
//        fireEvent(rootDirs, Collections.emptySet(), Collections.emptySet());

        try {
            directoryWatcher = DirectoryWatcher.builder()
                    .paths(rootDirs) // or use paths(directoriesToWatch)
                    .listener(event -> {
                        switch (event.eventType()) {
                            case CREATE, MODIFY: /* file created */
                                onFilesUpdated(Set.of(event.path().toFile()), Collections.emptySet(), Collections.emptySet());
                                break;
                            case DELETE: /* file deleted */
                                onFilesUpdated(Collections.emptySet(), Collections.emptySet(), Set.of(event.path().toFile()));
                                break;
                        }
                    })
                    // .fileHashing(false) // defaults to true
                    // .logger(logger) // defaults to LoggerFactory.getLogger(DirectoryWatcher.class)
                    // .watchService(watchService) // defaults based on OS to either JVM WatchService or the JNA macOS WatchService
                    .build();
            directoryWatcher.watchAsync();
        } catch (IOException ioe) {
            LOG.error("Exception while reading gallery root directories for DB indexing", ioe);
        }


    }

    /**
     * This is called when any files and directories are modified or deleted within the root directories. The job here
     * is to update the database appropriately
     * @param createdFiles
     * @param deletedFiles
     */
    private void onFilesUpdated(Set<File> createdFiles, Set<File> updatedFiles, Set<File> deletedFiles) {
        LOG.debug("onFilesUpdated(createdFiles: {}, deletedFiles: {}", createdFiles, deletedFiles);
        try {
            galleryAuthorizationService.loginAdminUser();
            Collection<File> rootDirectories = getRootDirectoriesForCurrentUser();
            List<File> allUpdatedFilesSorted = createdFiles.stream().sorted((a, b) -> getPathNameNoException(a).length() - getPathNameNoException(b).length()).collect(Collectors.toList());
            allUpdatedFilesSorted.forEach(f -> {
                try {
                    if (f.isDirectory()) {
                        createOrUpdateOneDirectory(f, rootDirectories);
                    } else {
                        createOrUpdateOneFile(f);
                    }
                } catch (ArangoDBException | IOException e) {
                    LOG.error("Error when updating {}. Ignoring", f, e);
                }
            });
            List<File> allDeletedFilesSorted = deletedFiles.stream().sorted((a, b) -> getPathNameNoException(b).length() - getPathNameNoException(a).length()).collect(Collectors.toList());
            allDeletedFilesSorted.forEach(df -> {
                try {
                    deleteOneVertexWithEdges(df);
                } catch (ArangoDBException | IOException e) {
                    LOG.error("Error when deleting {}. Ignoring", df, e);
                }
            });
        } catch (Exception e) {
            LOG.error("Error when updating files {} or deleting files {}. Aborting", createdFiles, deletedFiles, e);
        }
        finally {
            galleryAuthorizationService.logoutAdminUser();
        }
    }

    private GalleryFile createGalleryFileFromSearchResult(GalleryDocument galleryDocument) {
        try {
//            String path = galleryDocument.getPath().getProperties().get("path").toString();
            String path = galleryDocument.getPath();
            File realFile = new File(path);
            String publicPath = galleryService.getPublicPathFromRealFile(realFile);
            GalleryFile galleryFile = galleryService.createGalleryFile(publicPath, realFile);
            if (galleryDocument.getDateTaken() != null) {
                galleryFile.setDateTaken(Instant.parse(galleryDocument.getDateTaken()));
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

    public void searchTest() throws Exception {
//        String query =
//                "FOR v, e, p IN 1..5 OUTBOUND 'galleryImages/a96e85746f8732b97d86579f0387d8990ac3af62f5b4d2ce3187d7fe88d66b28'  galleryImageOwns " +
//                "FOR vv IN p.vertices " +
//                "RETURN DISTINCT v";
        final String query = "FOR v, e, p IN 1..5 OUTBOUND 'galleryImages/a96e85746f8732b97d86579f0387d8990ac3af62f5b4d2ce3187d7fe88d66b28'  galleryImageOwns " +
                "FILTER v.type == 'FILE'\n" +
                "LET fullArray = APPEND(FLATTEN(p.vertices[*].tags), FLATTEN(p.vertices[*].filenameParts)) \n" +
                "FILTER ['Michael'] ALL IN fullArray \n" +
                "RETURN DISTINCT v";
        ArangoCursor<GalleryDocument> cursor = db.query(query, null, null, GalleryDocument.class);
        cursor.forEachRemaining(aDocument -> {
            System.out.println("Key: " + aDocument.getKey());
        });
    }

    public void createOrUpdateAllDirectories() {
        try {
            galleryAuthorizationService.loginAdminUser();
            Collection<File> rootDirectories = getRootDirectoriesForCurrentUser();
            Collection<File> allDirectoriesCol = getAllDirectories(rootDirectories);
            List<File> allDirectoriesSorted = allDirectoriesCol.stream().sorted((a, b) -> getPathNameNoException(a).length() - getPathNameNoException(b).length()).collect(Collectors.toList());
            for (File oneDirectory : allDirectoriesSorted) {
                String currentDirKey = getKey(oneDirectory);
                try {
                    createOrUpdateOneDirectory(oneDirectory, rootDirectories);
                } catch (IOException | ArangoDBException e) {
                    LOG.error("Error while creating or updating directory in database", oneDirectory, e);
                }
                List<File> filesInDir = Arrays.stream(oneDirectory.listFiles()).filter(f -> f.isFile()).collect(Collectors.toList());
                for (File oneFile : filesInDir) {
                    try {
                        createOrUpdateOneFile(oneFile, currentDirKey);
                    } catch (IOException | ArangoDBException e) {
                        LOG.error("Failed in updating {}. Ignoring", oneFile, e);
                    }
                }
            }
        } catch (IOException | NotAllowedException e) {
            LOG.error("Error while creating or updating directories and files in database", e);
        } finally {
            galleryAuthorizationService.logoutAdminUser();
        }
    }

    private void createOrUpdateOneDirectory(File directory, Collection<File> rootDirectories) throws IOException {
        updateOneVertex(directory, VertexType.DIR);
        String currentDirKey = getKey(directory);
        String parentDirKey = "-1";
        if (rootDirectories.contains(directory)) {
            parentDirKey = ROOT_NODE_NAME;
        } else {
            parentDirKey = getKey(directory.getParentFile());
        }
        updateOneEdge(currentDirKey, parentDirKey);
    }

    private void createOrUpdateOneFile(File file) throws IOException {
        String parentDirKey = getKey(file.getParentFile());
        createOrUpdateOneFile(file, parentDirKey);
    }

    private void createOrUpdateOneFile(File file, String parentDirKey) throws IOException {
        if (!galleryService.isAllowedExtension(file)) {
            return;
        }
        updateOneVertex(file, VertexType.FILE);
        updateOneEdge(getKey(file), parentDirKey);
    }

    private String getPathNameNoException(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException ioe) {
            throw new RuntimeException("IOException when checking file path name. This should NOT happen!", ioe);
        }
    }

    private Collection<File> getRootDirectoriesForCurrentUser() throws IOException {
        Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
        return rootPathsForCurrentUser.values();
    }

    private void updateOneVertex(File file, VertexType vertexType) throws IOException {
        final String query =
                "UPSERT {_key: @key}\n" +
                        "INSERT {_key: @key, type: @type, tags: [], path: @path, dateTaken: @dateTaken, filenameParts: @filenameParts, gpsLatitude: @gpsLatitude, gpsLongitude: @gpsLongitude, physicalLocations: @physicalLocations, country: @country} \n" +
                        "UPDATE {path: @path, dateTaken: @dateTaken, filenameParts: @filenameParts, gpsLatitude: @gpsLatitude, gpsLongitude: @gpsLongitude} IN " + COLLECTION_NAME_GALLERY_IMAGES;
        String dirHash = getKey(file);
        Collection<String> filenameParts = filenameToSearchTermsStrategy.generateSearchTermsFromFilename(file);
        MetadataExtractionService.FileMetaData metadata = null;
        LocationResult locationMetadata = null;
        if (vertexType == VertexType.FILE) {
            metadata = metadataExtractionService.getMetadata(file);
            if (metadata.gpsLatitude() != null) {
                locationMetadata = locationMetadataService.getLocationMetadata(metadata.gpsLatitude(), metadata.gpsLongitude());
            }
        }
        Map<String, Object> bindVars = new MapBuilder()
                .put("key", dirHash)
                .put("path", file.getCanonicalPath())
                .put("type", vertexType.name())
                .put("filenameParts", filenameParts)
                .put("dateTaken", metadata != null && metadata.dateTaken() != null ? metadata.dateTaken().toString() : null)
                .put("gpsLatitude", metadata != null ? metadata.gpsLatitude() : null)
                .put("gpsLongitude", metadata != null ? metadata.gpsLongitude() : null)
                .put("physicalLocations", locationMetadata != null ? locationMetadata.getLocations() : Collections.EMPTY_LIST)
                .put("country", locationMetadata != null && locationMetadata.getCountryCode() != null ? new Locale("", locationMetadata.getCountryCode()).getDisplayName() : null)
                .get();
        db.query(query, bindVars, null, BaseDocument.class);
    }

    public void deleteOneVertexWithEdges(File file) throws IOException {
        String key = getKey(file);
        String node = COLLECTION_NAME_GALLERY_IMAGES + "/" + key;
        String query = """
                LET edgeKeys = (FOR v, e IN 1..1 ANY @node GRAPH 'galleryImageOwns' RETURN e._key)
                LET r = (FOR key IN edgeKeys REMOVE key IN galleryImageOwns)\s
                REMOVE @key IN galleryImages
                """;
        Map<String, Object> bindVars = new MapBuilder()
                .put("key", key)
                .put("node", node)
                .get();
        db.query(query, bindVars, null, BaseDocument.class);
    }

    private void updateOneEdge(String to, String from) throws IOException {
        final String query =
                "UPSERT {_from: @from, _to: @to}\n" +
                        "INSERT {_from: @from, _to: @to} \n" +
                        "UPDATE {_from: @from, _to: @to} IN " + GRAPH_NAME;
        String toString = COLLECTION_NAME_GALLERY_IMAGES + "/" + to;
        String fromString = COLLECTION_NAME_GALLERY_IMAGES + "/" + from;
//        Map<String, Object> bindVars = new MapBuilder().put("from", path).put("to", path).put("COLLECTION_NAME_GALLERY_IMAGES", COLLECTION_NAME_GALLERY_IMAGES).get();
        Map<String, Object> bindVars = new MapBuilder().put("from", fromString).put("to", toString).get();
        db.query(query, bindVars, null, BaseDocument.class);
    }

    private Collection<File> getAllDirectories(Collection<File> dirs) throws IOException, NotAllowedException {
        Collection<File> allDirectories = new HashSet<>();
        dirs.forEach(dir -> allDirectories.addAll(listFilesAndDirs(dir, FileFilterUtils.falseFileFilter(), FileFilterUtils.directoryFileFilter())));
        LOG.debug("Returning {} directories", allDirectories.size());
        return allDirectories;
    }

    private String getKey(File file) throws IOException {
        return DigestUtils.sha256Hex(file.getCanonicalPath());
    }

    public static class GalleryDocument extends BaseDocument {

        private enum FileType {
            FILE,
            DIRECTORY
        }

        private String key;

        private String dateTaken;

        private String path;

        private FileType type;

        private Double gpsLatitude;

        private Double gpsLongitude;

        private List<String> physicalLocations;

        private String[] tags;

        private String country;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getDateTaken() {
            return dateTaken;
        }

        public void setDateTaken(String dateTaken) {
            this.dateTaken = dateTaken;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public FileType getType() {
            return type;
        }

        public void setType(FileType type) {
            this.type = type;
        }

        public Double getGpsLatitude() {
            return gpsLatitude;
        }

        public void setGpsLatitude(Double gpsLatitude) {
            this.gpsLatitude = gpsLatitude;
        }

        public Double getGpsLongitude() {
            return gpsLongitude;
        }

        public void setGpsLongitude(Double gpsLongitude) {
            this.gpsLongitude = gpsLongitude;
        }

        public List<String> getPhysicalLocations() {
            return physicalLocations;
        }

        public void setPhysicalLocations(List<String> physicalLocations) {
            this.physicalLocations = physicalLocations;
        }

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(new GallerySearchServiceImpl().getKey(new File("/home/henrik/gallery-test-data/Bilder/2006-09 Graz")));
    }
}

