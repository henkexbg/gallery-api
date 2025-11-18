package com.github.henkexbg.gallery.bean;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;

public class DbFile {

    private long id;
    @ColumnName("path_on_disk")
    private String pathOnDisk;
    @ColumnName("is_directory")
    private Boolean isDirectory;
    private Geometry location;
    @ColumnName("location_name")
    private String locationName;
    @ColumnName("file_type")
    private String fileType;
    @ColumnName("content_type")
    private String contentType;
    @ColumnName("date_taken")
    private Instant dateTaken;
    @ColumnName("last_modified")
    private Instant lastModified;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPathOnDisk() {
        return pathOnDisk;
    }

    public void setPathOnDisk(String pathOnDisk) {
        this.pathOnDisk = pathOnDisk;
    }

    public Boolean getIsDirectory() {
        return isDirectory;
    }

    public void setIsDirectory(Boolean directory) {
        isDirectory = directory;
    }

    public Geometry getLocation() {
        return location;
    }

    public void setLocation(Geometry location) {
        this.location = location;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Instant getDateTaken() {
        return dateTaken;
    }

    public void setDateTaken(Instant dateTaken) {
        this.dateTaken = dateTaken;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }
}
