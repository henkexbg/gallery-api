package com.github.henkexbg.gallery.service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

public interface MetadataExtractionService {

    record FileMetaData(Instant dateTaken, Double gpsLatitude, Double gpsLongitude) {
    }

    FileMetaData getMetadata(File file) throws IOException;

}
