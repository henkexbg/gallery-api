/**
 * Copyright (c) 2016 Henrik Bjerne
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:The above copyright
 * notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 */
package com.github.henkexbg.gallery.controller.model;

import java.util.List;
import java.util.Map;

/**
 * POJO defining one gallery listing response. This response contains a number
 * of different attributes for the current directory, including sub-directories,
 * images and videos within this directory. This file will be sent back to the
 * requesting entity (of course with possible transformation to for instance
 * JSON).
 * 
 * @author Henrik Bjerne
 *
 */
public class ListingContext {

    private String currentPathDisplay;

    private String previousPath;

    private Map<String, String> directories;

    private List<GalleryFileHolder> images;

    private List<GalleryFileHolder> videos;

    private List<String> videoFormats;
    
    private boolean allowCustomImageSizes;
    
    private List<ImageFormat> imageFormats;

    public String getCurrentPathDisplay() {
        return currentPathDisplay;
    }

    public void setCurrentPathDisplay(String currentPathDisplay) {
        this.currentPathDisplay = currentPathDisplay;
    }

    public String getPreviousPath() {
        return previousPath;
    }

    public void setPreviousPath(String previousPath) {
        this.previousPath = previousPath;
    }

    public Map<String, String> getDirectories() {
        return directories;
    }

    public void setDirectories(Map<String, String> directories) {
        this.directories = directories;
    }

    public List<GalleryFileHolder> getImages() {
        return images;
    }

    public void setImages(List<GalleryFileHolder> images) {
        this.images = images;
    }

    public List<GalleryFileHolder> getVideos() {
        return videos;
    }

    public void setVideos(List<GalleryFileHolder> videos) {
        this.videos = videos;
    }

    public List<String> getVideoFormats() {
        return videoFormats;
    }

    public void setVideoFormats(List<String> videoFormats) {
        this.videoFormats = videoFormats;
    }

    public boolean isAllowCustomImageSizes() {
        return allowCustomImageSizes;
    }

    public void setAllowCustomImageSizes(boolean allowCustomImageSizes) {
        this.allowCustomImageSizes = allowCustomImageSizes;
    }

    public List<ImageFormat> getImageFormats() {
        return imageFormats;
    }

    public void setImageFormats(List<ImageFormat> imageFormats) {
        this.imageFormats = imageFormats;
    }
    
}
