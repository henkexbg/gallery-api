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
package com.github.henkexbg.gallery.bean;

import java.io.File;
import java.time.Instant;

/**
 * Simple POJO describing a gallery file. This links together a public path with
 * the actual file on the file system.
 * 
 * @author Henrik Bjerne
 *
 */
public class GalleryFile {

    public enum GalleryFileType {
        IMAGE, VIDEO;
    }

    private String publicPath;

    private File actualFile;

    private GalleryFileType type;

    private String contentType;

    private Instant dateTaken;

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public File getActualFile() {
        return actualFile;
    }

    public void setActualFile(File actualFile) {
        this.actualFile = actualFile;
    }

    public GalleryFileType getType() {
        return type;
    }

    public void setType(GalleryFileType type) {
        this.type = type;
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

    @Override
    public String toString() {
        return "GalleryFile{" +
                "publicPath='" + publicPath + '\'' +
                ", actualFile=" + actualFile +
                ", type=" + type +
                ", contentType='" + contentType + '\'' +
                ", dateTaken=" + dateTaken +
                '}';
    }
}
