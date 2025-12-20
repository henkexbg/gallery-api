package com.github.henkexbg.gallery.bean;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.locationtech.jts.geom.Geometry;

public class Location {

    private long id;
    @ColumnName("the_geom")
    private Geometry theGeom;
    private String name;
    @ColumnName("country_iso_a2")
    private String countryIsoA2;
    @ColumnName("feature_code")
    private String featureCode;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Geometry getTheGeom() {
        return theGeom;
    }

    public void setTheGeom(Geometry theGeom) {
        this.theGeom = theGeom;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountryIsoA2() {
        return countryIsoA2;
    }

    public void setCountryIsoA2(String countryIsoA2) {
        this.countryIsoA2 = countryIsoA2;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }
}
