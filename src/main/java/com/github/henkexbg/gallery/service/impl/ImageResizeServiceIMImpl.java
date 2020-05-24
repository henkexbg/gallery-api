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
package com.github.henkexbg.gallery.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.service.ImageResizeService;

/**
 * {@link ImageResizeService} using ImageMagick via IM4J wrapper. If this
 * service is to be used, ImageMagick needs to be installed on the system.
 * 
 * @author Henrik Bjerne
 *
 */
public class ImageResizeServiceIMImpl implements ImageResizeService {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private String imageMagickPath;

    public void setImageMagickPath(String imageMagickPath) {
        this.imageMagickPath = imageMagickPath;
    }

    private String backgroundColor = "black";

    @Override
    public void resizeImage(File origImage, File newImage, int width, int height) throws IOException {
        LOG.debug("Entering resizeImage(origImage={}, width={}, height={})", origImage, width, height);
        long startTime = System.currentTimeMillis();
        ConvertCmd cmd = new ConvertCmd();

        if (StringUtils.isNotBlank(imageMagickPath)) {
            cmd.setSearchPath(imageMagickPath);
        }
        IMOperation op = new IMOperation();
        op.addImage(origImage.toString());
        op.resize(width, height);
        op.gravity("center");
        op.background(backgroundColor);
        op.extent(width, height);
        // op.density(96, 96);
        op.quality(70d);
        op.addImage(newImage.toString());

        // execute the operation
        try {
            cmd.run(op);
        } catch (IM4JavaException | InterruptedException e) {
            throw new IOException(e);
        }
        long duration = System.currentTimeMillis() - startTime;
        LOG.debug("Time in milliseconds to scale {}: {}", newImage.toString(), duration);
    }

    @Override
    public void generateCompositeImage(List<File> origImages, File newImage, int width, int height) throws IOException {
        resizeImage(origImages.get(0), newImage, width, height);
    }

}
