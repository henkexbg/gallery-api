package com.github.henkexbg.gallery.bean;

import java.util.List;

public class LocationResult {

    private List<String> locations;

    private String countryCode;

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
}
