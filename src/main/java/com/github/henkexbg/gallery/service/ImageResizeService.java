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
package com.github.henkexbg.gallery.service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Simple interface for resizing an image. This leaves some interpretation to
 * the implementation, especially if the ratio is not the same as of the
 * original image.
 * 
 * @author Henrik Bjerne
 *
 */
public interface ImageResizeService {

    /**
     * Resizes one image.
     * 
     * @param origImage
     *            Original image.
     * @param newImage
     *            Path of new image. This image should not exist. If it does, it
     *            is ok for the implementation to remove the existing file.
     * @param width
     *            Width to rescale to.
     * @param height
     *            Height to rescale to.
     * @throws IOException
     *             If there is a problem accessing any of the files.
     */
    void resizeImage(File origImage, File newImage, int width, int height) throws IOException;

    /**
     * Generates a composite image out of several input images, for example an image representing a directory.
     *
     * @param origImages
     *            Original images. Can be any number, but the implementation may just use a subset.
     * @param newImage
     *            Path of new image. This image should not exist. If it does, it
     *            is ok for the implementation to remove the existing file.
     * @param width
     *            Width to rescale to.
     * @param height
     *            Height to rescale to.
     * @throws IOException
     *             If there is a problem accessing any of the files.
     */
    void generateCompositeImage(List<File> origImages, File newImage, int width, int height) throws IOException;

}
