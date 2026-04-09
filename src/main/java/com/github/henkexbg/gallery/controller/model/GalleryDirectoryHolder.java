package com.github.henkexbg.gallery.controller.model;

/**
 * POJO defining one gallery directory. This directory will be sent back to the requesting entity (of course with possible transformation to
 * for instance JSON).
 *
 * @author Henrik Bjerne
 *
 */
public class GalleryDirectoryHolder {

    private String name;

    private String path;

    private String parentPath;

    private GalleryFileHolder image;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public GalleryFileHolder getImage() {
        return image;
    }

    public void setImage(GalleryFileHolder image) {
        this.image = image;
    }


}
