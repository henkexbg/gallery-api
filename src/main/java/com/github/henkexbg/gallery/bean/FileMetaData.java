package com.github.henkexbg.gallery.bean;

import java.time.Instant;

public record FileMetaData (Instant dateTaken, double gpsLongitude, double gpsLatitude) {
}
