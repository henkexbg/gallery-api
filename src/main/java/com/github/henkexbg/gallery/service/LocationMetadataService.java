package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.bean.LocationMetadata;
import com.github.henkexbg.gallery.bean.LocationResult;

import java.io.IOException;

public interface LocationMetadataService {

//    record LocationMetadata(String key, String city, double latitude, double longitude, String countryCode) {
//    }

    LocationResult getLocationMetadata(double latitude, double longitude);

    void createAndLoadCollection() throws IOException;

}
