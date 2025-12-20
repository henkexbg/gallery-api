package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.util.GalleryFileUtils;
import jakarta.annotation.Resource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class LocationLoader {

    private static final int CSV_INDEX_ID = 0;
    private static final int CSV_INDEX_NAME = 1;
    private static final int CSV_INDEX_LATITUDE = 4;
    private static final int CSV_INDEX_LONGITUDE = 5;
    private static final int CSV_INDEX_FEATURE_CODE = 7;
    private static final int CSV_INDEX_COUNTRY = 8;

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    Jdbi jdbi;

    @Value("${gallery.location.source.default.uri}")
    URI sourceDefaultUri;

    public void loadDataFromGeonames(URI locationFileUri) throws IOException {
        File locationFile = null;
        try {
            if (locationFileUri == null) {
                locationFileUri = sourceDefaultUri;
            }
            if (locationFileUri.getScheme() != null && locationFileUri.getScheme().startsWith("http")) {
                try (HttpClient httpClient = HttpClient.newHttpClient()) {
                    // DOWNLOADING
                    HttpRequest request = HttpRequest.newBuilder(locationFileUri).GET().build();
                    HttpResponse<Path> countries =
                            httpClient.send(request, HttpResponse.BodyHandlers.ofFile(Files.createTempFile("countries", "")));
                    locationFile = countries.body().toFile();
                    LOG.info("Downloaded file: {}", countries.uri());
                } catch (Exception e) {
                    LOG.error("Error when downloading location file", e);
                    throw new IOException("Error when downloading location file", e);
                }
            } else if (locationFileUri.getScheme() != null && locationFileUri.getScheme().startsWith("file")) {
                locationFile = new File(locationFileUri);
            } else {
                throw new IOException("Invalid location file URI: %s".formatted(locationFileUri));
            }
            if ("application/zip".equals(GalleryFileUtils.getContentType(locationFile))) {
                //UNZIPPING
                locationFile = unzipAndDeleteZippedFile(locationFile);
            }

            // PARSING AND LOADING
            Iterable<CSVRecord> records = CSVFormat.TDF.parse(new FileReader(locationFile));
            final String mergeQueryChildDir = """
                    MERGE INTO location (id, the_geom, name, country_iso_a2, feature_code)
                    KEY (id)
                    VALUES (:id, ST_GeomFromText(:the_geom, 4326), :name, :country_iso_a2, :feature_code)
                    """;
            jdbi.useTransaction(trx -> {
                // This loop is more convoluted that one would like, but the CSV parser might throw exceptions on
                // iterator.hasNext(), and there is no nice way of ignoring them
                Iterator<CSVRecord> iterator = records.iterator();
                long startTime = System.currentTimeMillis();
                long counter = 0;
                while (true) {
                    try {
                        if (!iterator.hasNext()) {
                            break;
                        }
                        CSVRecord record = iterator.next();
                        Integer pk = Integer.parseInt(record.get(CSV_INDEX_ID));
                        String name = record.get(CSV_INDEX_NAME);
                        BigDecimal lat = new BigDecimal(record.get(CSV_INDEX_LATITUDE));
                        BigDecimal lon = new BigDecimal(record.get(CSV_INDEX_LONGITUDE));
                        String featureCode = record.get(CSV_INDEX_FEATURE_CODE);
                        String countryIso = record.get(CSV_INDEX_COUNTRY);
                        String point = "POINT(%s %s)".formatted(lon, lat);
                        counter++;
                        if (counter % 50000 == 0) {
                            LOG.info("Loaded {} locations. Speed {} records/s", counter,
                                    (double) counter / ((System.currentTimeMillis() - startTime) / 1000));
                        }
                        trx.createUpdate(mergeQueryChildDir).bind("id", pk).bind("name", name).bind("the_geom", point)
                                .bind("country_iso_a2", countryIso).bind("feature_code", featureCode).execute();

                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            });
        } finally {
            if (locationFile != null) {
                FileUtils.deleteQuietly(locationFile);
            }
        }
    }

    File unzipAndDeleteZippedFile(File zippedFile) throws IOException {
        File unzippedFile;
        byte[] buffer = new byte[65536];
        try (FileInputStream fis = new FileInputStream(zippedFile); ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry ze = zis.getNextEntry();
            if (ze == null) {
                throw new IOException("Zip entry is empty");
            }
            if (ze.isDirectory()) {
                throw new IOException("Directories in ZIP file are not supported");
            }
            unzippedFile = Files.createTempFile("gallery", "temp").toFile();
            System.out.println("Unzipping to " + unzippedFile.getAbsolutePath());
            FileOutputStream fos = new FileOutputStream(unzippedFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zis.closeEntry();
            return unzippedFile;
        } catch (IOException e) {
            LOG.error("Unzipping of {} failed with message {}", zippedFile, e.getMessage(), e);
            throw e;
        } finally {
            if (zippedFile != null) {
                FileUtils.deleteQuietly(zippedFile);
            }
        }
    }

}
