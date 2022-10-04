package com.github.henkexbg.gallery.service.impl;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.CollectionEntity;
import com.arangodb.model.DocumentImportOptions;
import com.arangodb.model.GeoIndexOptions;
import com.arangodb.util.MapBuilder;
import com.github.henkexbg.gallery.bean.LocationMetadata;
import com.github.henkexbg.gallery.bean.LocationResult;
import com.github.henkexbg.gallery.service.LocationMetadataService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class LocationMetadataServiceImpl implements LocationMetadataService {

    public static final String COLLECTION_NAME_LOCATIONS = "locations";

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private ArangoDatabase db;

    private String dbHost;

    private Integer dbPort;

    private String dbName = "_system";

    private String dbUsername;

    private String dbPassword;

    private File csvFile;

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

    public void setCsvFile(File csvFile) {
        this.csvFile = csvFile;
    }

    @PostConstruct
    public void init() {
        ArangoDB.Builder arangoDBBuilder = new ArangoDB.Builder().user(dbUsername).password(dbPassword);
        if (StringUtils.isNotBlank(dbHost) && dbPort > 0) {
            arangoDBBuilder.host(dbHost, dbPort);
        }
        db = arangoDBBuilder.build().db(dbName);
    }

    public LocationResult getLocationMetadata(double latitude, double longitude) {
        String query = """
                LET allClosestLocations = (
                  FOR x IN locations
                    LET distance = DISTANCE(@latitude, @longitude, x.latitude, x.longitude)
                    FILTER distance < 50000
                    SORT distance ASC
                    LIMIT 10000
                    FILTER x.featureCode == 'PPLX' || x.featureCode == 'PPLA' || x.featureCode == 'PPLA2'
                    RETURN x
                )
                                
                LET countryCode = FIRST(allClosestLocations).countryCode
                                
                LET list = (
                  FOR x IN allClosestLocations
                    FILTER x.countryCode == countryCode
                    RETURN x
                )
                                
                LET pplx = (
                  FOR x IN list
                    FILTER x.featureCode == 'PPLX'
                    RETURN x.name
                  )
                                
                LET ppla = (
                  FOR x IN list
                    FILTER x.featureCode == 'PPLA'
                    RETURN x.name
                  )
                                
                LET ppla2 = (
                  FOR x IN list
                    FILTER x.featureCode == 'PPLA2'
                    RETURN x.name
                  )
                                
                LET locations = (
                  FOR x in [FIRST(pplx), FIRST(ppla2), FIRST(ppla)]
                    FILTER x != null
                    RETURN x
                )
                                
                RETURN {locations: locations, countryCode: countryCode}""";
        Map<String, Object> bindVars = new MapBuilder().put("latitude", latitude).put("longitude", longitude).get();
        ArangoCursor<LocationResult> cursor = db.query(query, bindVars, null, LocationResult.class);
        if (cursor.hasNext()) {
            return cursor.next();
        }
        return null;
    }

//    public record LocationMetadataDocument(String key, String city, double latitude, double longitude,
//                                           String countryCode) {
//    }


    public void createAndLoadCollection() throws IOException {
        final int BATCH_SIZE = 5000;
        final int CSV_INDEX_ID = 0;
        final int CSV_INDEX_NAME = 1;
        final int CSV_INDEX_LATITUDE = 4;
        final int CSV_INDEX_LONGITUDE = 5;
        final int CSV_INDEX_FEATURE_CLASS = 6;
        final int CSV_INDEX_FEATURE_CODE = 7;
        final int CSV_INDEX_COUNTRY = 8;
        final int CSV_INDEX_TIMEZONE = 17;
        final Set<String> ALLOWED_FEATURE_CODES = Set.of("PPLX", "PPLA", "PPLA2");
        ArangoCollection collection = createCollectionIfNotExisting();
        Reader in = new FileReader(csvFile);
        Iterable<CSVRecord> records = CSVFormat.TDF.parse(in);

        int counter = 0;
        long startTime = System.currentTimeMillis();
        List<LocationMetadata> docsInBatch = new ArrayList<>(BATCH_SIZE);
        Iterator<CSVRecord> iterator = records.iterator();
        // This loop is more convoluted that one would like, but the CSV parser might throw exceptions on
        // iterator.hasNext(), and there is no nice way of ignoring them
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                CSVRecord record = iterator.next();
                String featureCode = record.get(CSV_INDEX_FEATURE_CODE);
                if (!ALLOWED_FEATURE_CODES.contains(featureCode)) {
                    continue;
                }
                String id = record.get(CSV_INDEX_ID);
                String name = record.get(CSV_INDEX_NAME);
                double latitude = Double.parseDouble(record.get(CSV_INDEX_LATITUDE));
                double longitude = Double.parseDouble(record.get(CSV_INDEX_LONGITUDE));
                String featureClass = record.get(CSV_INDEX_FEATURE_CLASS);
                String countryCode = record.get(CSV_INDEX_COUNTRY);
                String timeZone = record.get(CSV_INDEX_TIMEZONE);
                LocationMetadata oneDoc = new LocationMetadata(id, name, latitude, longitude, featureClass, featureCode, countryCode, timeZone);
                docsInBatch.add(oneDoc);
                if (docsInBatch.size() == BATCH_SIZE) {
                    importBatch(collection, docsInBatch);
                    // Clear for next batch
                    docsInBatch.clear();
                }
                counter++;
                if (counter % 50000 == 0) {
                    LOG.info("Loaded {} locations. Speed {}/s", counter, (double) counter / ((System.currentTimeMillis() - startTime) / 1000));
                }
            } catch (Exception e) {
                LOG.error("Error at line {}. Skipping", counter, e);
            }
        }
        if (!docsInBatch.isEmpty()) {
            // Import the tail end of docs that weren't a full batch size
            importBatch(collection, docsInBatch);
        }
        LOG.info("Imported {} documents in {} milliseconds", counter, System.currentTimeMillis() - startTime);
    }

    private void importBatch(ArangoCollection collection, List<LocationMetadata> docs) {
        DocumentImportOptions opts = new DocumentImportOptions();
        opts.onDuplicate(DocumentImportOptions.OnDuplicate.replace);
        collection.importDocuments(docs, opts);
    }

    private ArangoCollection createCollectionIfNotExisting() {
        ArangoCollection collection = db.collection(COLLECTION_NAME_LOCATIONS);
        if (!collection.exists()) {
            collection.create();
            GeoIndexOptions geoIndexOptions = new GeoIndexOptions();
            geoIndexOptions.geoJson(false);
            geoIndexOptions.inBackground(true);
            geoIndexOptions.name("geoIndex");
            collection.ensureGeoIndex(List.of("latitude", "longitude"), geoIndexOptions);
        }
        return collection;
    }

}
