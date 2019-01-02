package com.github.henkexbg.gallery.service.impl;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.util.MapBuilder;
import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GallerySearchService;
import com.github.henkexbg.gallery.service.GalleryService;
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
        if(StringUtils.isNotBlank(dbHost) && dbPort > 0) {
            arangoDBBuilder.host(dbHost, dbPort);
        }
        db = arangoDBBuilder.build().db(dbName);
    }

    @Override
    public List<GalleryFile> search(String publicPath, String searchTerm) throws IOException, NotAllowedException {
        File realFileOrDir = galleryService.getRealFileOrDir(publicPath);
        String publicRoot = galleryService.getPublicRootFromRealFile(realFileOrDir);
        String key = publicPath != null ? getKey(realFileOrDir.getCanonicalPath()) : ROOT_NODE_NAME;
        LOG.debug("Performing search with publicPath={}, key={} searchTerm={}", publicPath, key, searchTerm);
        String startDoc = COLLECTION_NAME_GALLERY_IMAGES + "/" + key;
        final String query = "FOR v, e, p IN 1..5 OUTBOUND @startDoc " + GRAPH_NAME + "\n" +
                "FILTER v.type == 'FILE'\n" +
                "LET fullArray = APPEND(FLATTEN(p.vertices[*].tags), FLATTEN(p.vertices[*].filenameParts))\n" +
                "FILTER @searchTerms ALL IN fullArray\n" +
                "RETURN DISTINCT v";
        LOG.debug("Final query: {}", query);
        Map<String, Object> bindVars = new MapBuilder().put("startDoc", startDoc).put("searchTerms", searchTerm.split("\\s+")).get();
        ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
        List<GalleryFile> galleryFiles = new ArrayList<>();
        cursor.forEachRemaining(aDocument -> {
            GalleryFile oneGalleryFile = createGalleryFileFromSearchResult(publicRoot, aDocument);
            if (oneGalleryFile != null) {
                galleryFiles.add(oneGalleryFile);
            }
        });
        LOG.debug("Returning {} gallery files", galleryFiles.size());
        return galleryFiles;
    }

    private GalleryFile createGalleryFileFromSearchResult(String publicRoot, BaseDocument baseDocument) {
        try {
            String path = baseDocument.getProperties().get("path").toString();
            File realFile = new File(path);
            String publicPath = galleryService.getPublicPathFromRealFile(publicRoot, realFile);
            return galleryService.createGalleryFile(publicPath, realFile);
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
        ArangoCursor<BaseDocument> cursor = db.query(query, null, null, BaseDocument.class);
        cursor.forEachRemaining(aDocument -> {
            System.out.println("Key: " + aDocument.getKey());
        });
    }

    public void createOrUpdateAllDirectories() {
        try {
            galleryAuthorizationService.loginAdminUser();
            Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
            Collection<File> allDirectoriesCol = getAllDirectories(rootPathsForCurrentUser.values());
            List<File> allDirectoriesSorted = allDirectoriesCol.stream().sorted((a,b) -> getPathNameNoException(a).length() - getPathNameNoException(b).length()).collect(Collectors.toList());
            Collection<File> rootDirectories = rootPathsForCurrentUser.values();
            for (File oneDirectory : allDirectoriesSorted) {
                updateOneVertex(oneDirectory, VertexType.DIR);
                String currentDirKey = getKey(oneDirectory.getCanonicalPath());
                String parentDirKey = "-1";
                if (rootDirectories.contains(oneDirectory)) {
                    parentDirKey = ROOT_NODE_NAME;
                } else {
                    parentDirKey = getKey(oneDirectory.getParentFile().getCanonicalPath());
                }
                updateOneEdge(currentDirKey, parentDirKey);
                List<File> filesInDir = Arrays.stream(oneDirectory.listFiles()).filter(f -> f.isFile() && galleryService.isAllowedExtension(f)).collect(Collectors.toList());
                for (File oneFile : filesInDir) {
                    updateOneVertex(oneFile, VertexType.FILE);
                    updateOneEdge(getKey(oneFile.getCanonicalPath()), currentDirKey);
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
        } catch(IOException ioe) {
            throw new RuntimeException("IOException when checking file path name. This should NOT happen!", ioe);
        }
    }

    private void updateOneVertex(File file, VertexType vertexType) throws IOException {
        final String query =
                "UPSERT {_key: @key}\n" +
                        "INSERT {_key: @key, path: @path, type: @type, filenameParts: @filenameParts, tags: []} \n" +
                        "UPDATE {path: @path, filenameParts: @filenameParts} IN " + COLLECTION_NAME_GALLERY_IMAGES;
        String dirHash = getKey(file.getCanonicalPath());
        Collection<String> filenameParts = filenameToSearchTermsStrategy.generateSearchTermsFromFilename(file.getName());
        Map<String, Object> bindVars = new MapBuilder().put("key", dirHash).put("path", file.getCanonicalPath()).put("type", vertexType.name()).put("filenameParts", filenameParts).get();
        ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
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
        ArangoCursor<BaseDocument> cursor = db.query(query, bindVars, null, BaseDocument.class);
    }

    private Collection<File> getAllDirectories(Collection<File> dirs) throws IOException, NotAllowedException {
        Collection<File> allDirectories = new HashSet<>();
        dirs.forEach(dir -> allDirectories.addAll(listFilesAndDirs(dir, FileFilterUtils.falseFileFilter(), FileFilterUtils.directoryFileFilter())));
        LOG.debug("Returning {} directories", allDirectories.size());
        return allDirectories;
    }

    private String getKey(String originalString) {
        return DigestUtils.sha256Hex(originalString);
    }
}

