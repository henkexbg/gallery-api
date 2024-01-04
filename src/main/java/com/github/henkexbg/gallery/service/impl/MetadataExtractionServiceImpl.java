package com.github.henkexbg.gallery.service.impl;

import com.github.henkexbg.gallery.service.MetadataExtractionService;
import com.thebuzzmedia.exiftool.ExifTool;
import com.thebuzzmedia.exiftool.ExifToolBuilder;
import com.thebuzzmedia.exiftool.Tag;
import com.thebuzzmedia.exiftool.core.StandardTag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public class MetadataExtractionServiceImpl implements MetadataExtractionService {

    private static final String CREATE_DATE_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss";
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private final DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .appendPattern(CREATE_DATE_DATE_FORMAT)
            .toFormatter()
            .withZone(ZoneId.of("UTC"));

    private String exiftoolPath;

    private ExifTool exifTool;

    public void setExiftoolPath(String exiftoolPath) {
        this.exiftoolPath = exiftoolPath;
    }

    @PostConstruct
    public void init() {
        exifTool = new ExifToolBuilder().withPath(exiftoolPath).enableStayOpen().build();
    }


    public FileMetaData getMetadata(File file) throws IOException {
        List<Tag> tags = List.of(StandardTag.CREATE_DATE, StandardTag.GPS_LATITUDE, StandardTag.GPS_LONGITUDE);
        Map<Tag, String> imageMeta = exifTool.getImageMeta(file, tags);
        Instant dateTaken = null;
        try {
            String dateString = imageMeta.get(StandardTag.CREATE_DATE);
            if (dateString != null) {
                dateTaken = dateTimeFormatter.parse(dateString, Instant::from);
            }
        } catch (DateTimeParseException dtpe) {
            LOG.warn("Could not retrieve {} from image EXIF. Using lastModified on file", file.getCanonicalPath());
        }

        Double gpsLatitude = null, gpsLongitude = null;
        try {
            String gpsLatString = imageMeta.get(StandardTag.GPS_LATITUDE);
            if (gpsLatString != null) {
                gpsLatitude = Double.parseDouble(gpsLatString);
            }
            String gpsLongString = imageMeta.get(StandardTag.GPS_LONGITUDE);
            if (gpsLongString != null) {
                gpsLongitude = Double.parseDouble(gpsLongString);
            }
        } catch (NumberFormatException nfe) {
            LOG.warn("Invalid GPS coordinates for {}", file.getCanonicalPath());
        }
        if (gpsLatitude == null || gpsLongitude == null) {
            gpsLatitude = null;
            gpsLongitude = null;
        }

        FileMetaData fileMetaData = new FileMetaData(dateTaken, gpsLatitude, gpsLongitude);
        LOG.debug("Returning file metadata for {}: {}", file.getCanonicalPath(), fileMetaData);
        return fileMetaData;
    }

    @PreDestroy
    public void onClose() {
        try {
            exifTool.close();
        } catch (Exception e) {}
    }

}
