package com.github.henkexbg.gallery.bean;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.locationtech.jts.geom.Geometry;

public class Location {

    private Integer pk;
    @ColumnName("the_geom")
    private Geometry theGeom;
    private String name;
    @ColumnName("country_name")
    private String countryName;
    @ColumnName("country_iso_a2")
    private String countryIsoA2;
    @ColumnName("adm1name")
    private String adm1Name;

    public Integer getPk() {
        return pk;
    }

    public void setPk(Integer pk) {
        this.pk = pk;
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

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCountryIsoA2() {
        return countryIsoA2;
    }

    public void setCountryIsoA2(String countryIsoA2) {
        this.countryIsoA2 = countryIsoA2;
    }

    public String getAdm1Name() {
        return adm1Name;
    }

    public void setAdm1Name(String adm1Name) {
        this.adm1Name = adm1Name;
    }
}
