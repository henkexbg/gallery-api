/**
 * Copyright (c) 2016 Henrik Bjerne
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:The above copyright
 * notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.henkexbg.gallery.service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Methods related to video conversion. A number of video modes can be set up.
 * The idea here is that for instance different quality settings can be catered
 * for.
 *
 * @author Henrik Bjerne
 *
 */
public interface VideoConversionService {

    /**
     * The available video modes.
     *
     * @return A list of available modes.
     */
    Collection<String> getAvailableVideoModes();

    /**
     * Converts a video to the given video mode.
     *
     * @param origVideo
     *            Original video.
     * @param newVideo
     *            New video. The file should not exist. If it does it is ok for
     *            the implementation to remove it.
     * @param videoMode
     *            The video mode. Has to be one of the values returned by
     *            {@link #getAvailableVideoModes()}.
     * @throws IOException
     *             If video cannot be converted.
     */
    void convertVideo(File origVideo, File newVideo, String videoMode) throws IOException;

    /**
     * Generates an image for the video, to be used for example as a thumbnail.
     * @param video Video
     * @param image Name of image to generate. The file should not exist. If it
     *              does it is ok for the implementation to remove it.
     * @param width Width of image to be generated
     * @param height Height of image to be generated
     * @throws IOException If image cannot be generated.
     */
    void generateImageForVideo(File video, File image, int width, int height) throws IOException;

}
