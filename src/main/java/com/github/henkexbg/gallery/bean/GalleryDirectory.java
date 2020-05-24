package com.github.henkexbg.gallery.bean;

import com.github.henkexbg.gallery.bean.GalleryFile;

import java.io.File;

/**
 * Simple POJO describing a gallery directory. This links together a public path with
 * the actual directory on the file system.
 *
 * @author Henrik Bjerne
 *
 */
public class GalleryDirectory {

    private String name;

    private GalleryFile image;

    private String publicPath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GalleryFile getImage() {
        return image;
    }

    public void setImage(GalleryFile image) {
        this.image = image;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }
}
