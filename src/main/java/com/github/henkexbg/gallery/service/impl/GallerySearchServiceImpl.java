package com.github.henkexbg.gallery.service.impl;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.util.MapBuilder;
import com.github.henkexbg.gallery.bean.LocationResult;
import com.github.henkexbg.gallery.service.*;
import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.listFilesAndDirs;

public class GallerySearchServiceImpl implements GallerySearchService {

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

    @Required
    public void setGalleryAuthorizationService(GalleryAuthorizationService galleryAuthorizationService) {
        this.galleryAuthorizationService = galleryAuthorizationService;
    }

    @Required
    public void setGalleryService(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @Required
    public void setFilenameToSearchTermsStrategy(FilenameToSearchTermsStrategy filenameToSearchTermsStrategy) {
        this.filenameToSearchTermsStrategy = filenameToSearchTermsStrategy;
    }

    @Required
    public void setMetadataExtractionService(MetadataExtractionService metadataExtractionService) {
        this.metadataExtractionService = metadataExtractionService;
    }

    @Required
    public void setLocationMetadataService(LocationMetadataService locationMetadataService) {
        this.locationMetadataService = locationMetadataService;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public void setDbPort(Integer dbPort) {
        this.dbPort = dbPort;
    }

    @Required
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    @Required
    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    @Required
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
            Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
            Collection<File> allDirectoriesCol = getAllDirectories(rootPathsForCurrentUser.values());
            List<File> allDirectoriesSorted = allDirectoriesCol.stream().sorted((a, b) -> getPathNameNoException(a).length() - getPathNameNoException(b).length()).collect(Collectors.toList());
            Collection<File> rootDirectories = rootPathsForCurrentUser.values();
            for (File oneDirectory : allDirectoriesSorted) {
                updateOneVertex(oneDirectory, VertexType.DIR);
                String currentDirKey = getKey(oneDirectory);
                String parentDirKey = "-1";
                if (rootDirectories.contains(oneDirectory)) {
                    parentDirKey = ROOT_NODE_NAME;
                } else {
                    parentDirKey = getKey(oneDirectory.getParentFile());
                }
                updateOneEdge(currentDirKey, parentDirKey);
                List<File> filesInDir = Arrays.stream(oneDirectory.listFiles()).filter(f -> f.isFile() && galleryService.isAllowedExtension(f)).collect(Collectors.toList());
                for (File oneFile : filesInDir) {
                    updateOneVertex(oneFile, VertexType.FILE);
                    updateOneEdge(getKey(oneFile), currentDirKey);
                }
            }
        } catch (Exception e) {
            LOG.error("Error while creating and updating all directories into database", e);
        } finally {
            galleryAuthorizationService.logoutAdminUser();
        }
    }

    private String getPathNameNoException(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException ioe) {
            throw new RuntimeException("IOException when checking file path name. This should NOT happen!", ioe);
        }
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
                String apa = "1";
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

