/**
 * Copyright (c) 2016 Henrik Bjerne
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package com.github.henkexbg.gallery.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.service.ImageResizeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ImageResizeService} using ImageMagick via IM4J wrapper. If this service is to be used, ImageMagick needs to be installed on the
 * system.
 *
 * @author Henrik Bjerne
 *
 */
@Component
@ConditionalOnProperty(name = "gallery.resizing.method", havingValue = "IM")
public class ImageResizeServiceIMImpl implements ImageResizeService {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Value("${gallery.imageMagickPath:}")
    String imageMagickPath;

    @Override
    public void resizeImage(File origImage, File newImage, int width, int height) throws IOException {
        LOG.debug("Entering resizeImage(origImage={}, width={}, height={})", origImage, width, height);
        long startTime = System.currentTimeMillis();
        File dir = newImage.getParentFile();
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        ConvertCmd cmd = new ConvertCmd();

        if (StringUtils.isNotBlank(imageMagickPath)) {
            cmd.setSearchPath(imageMagickPath);
        }
        IMOperation op = new IMOperation();
        op.addImage(origImage.toString());
        op.resize(width, height);
        op.quality(80d);
        op.addImage(newImage.toString());

        // execute the operation
        try {
            cmd.run(op);
        } catch (IM4JavaException | InterruptedException e) {
            throw new IOException(e);
        }
        LOG.debug("Time in milliseconds to scale {}: {}", newImage, System.currentTimeMillis() - startTime);
    }

    @Override
    public void generateCompositeImage(List<File> origImages, File newImage, int width, int height) throws IOException {
        LOG.debug("Entering generateCompositeImage(origImages.size={}, newImage={}, width={}, height={})",
                origImages == null ? null : origImages.size(), newImage, width, height);
        if (origImages == null || origImages.isEmpty()) {
            throw new IOException("At least one image required to build a composite image!");
        }
        long startTime = System.currentTimeMillis();
        File dir = newImage.getParentFile();
        if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
        }
        // If only one image, delegate to resize logic
        if (origImages.size() == 1) {
            resizeImage(origImages.getFirst(), newImage, width, height);
            return;
        }

        int maxImages = Math.min(4, origImages.size());
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);

        ConvertCmd cmd = new ConvertCmd();
        if (StringUtils.isNotBlank(imageMagickPath)) {
            cmd.setSearchPath(imageMagickPath);
        }
        IMOperation op = new IMOperation();

        try {
            switch (maxImages) {
                case 2: {
                    // Two images side-by-side, each sized to width/2 x height
                    for (int i = 0; i < 2; i++) {
                        File f = origImages.get(i);
                        op.addImage(f.toString());
                        op.addRawArgs("-resize", halfW + "x" + height + "^");
                        op.gravity("center");
                        op.extent(halfW, height);
                    }
                    op.addRawArgs("+append"); // horizontal append
                    break;
                }
                case 3: {
                    // Layout: left large (w/2 x h) and right two stacked (w/2 x h/2)
                    // Left
                    op.openOperation();
                    op.addImage(origImages.getFirst().toString());
                    op.addRawArgs("-resize", halfW + "x" + height + "^");
                    op.gravity("center");
                    op.extent(halfW, height);
                    op.closeOperation();

                    // Right column (two stacked)
                    op.openOperation();
                    for (int i = 1; i <= 2; i++) {
                        op.addImage(origImages.get(i).toString());
                        op.addRawArgs("-resize", halfW + "x" + halfH + "^");
                        op.gravity("center");
                        op.extent(halfW, halfH);
                    }
                    op.addRawArgs("-append"); // vertical append to stack
                    op.closeOperation();

                    // Append left and right horizontally
                    op.addRawArgs("+append");
                    break;
                }
                default: { // 4 or more
                    // Use first 4, arranged in 2x2 grid, each cell width/2 x height/2
                    // Top row
                    op.openOperation();
                    for (int i = 0; i < 2; i++) {
                        op.addImage(origImages.get(i).toString());
                        op.addRawArgs("-resize", halfW + "x" + halfH + "^");
                        op.gravity("center");
                        op.extent(halfW, halfH);
                    }
                    op.addRawArgs("+append"); // make top row
                    op.closeOperation();

                    // Bottom row
                    op.openOperation();
                    for (int i = 2; i < 4; i++) {
                        op.addImage(origImages.get(i).toString());
                        op.addRawArgs("-resize", halfW + "x" + halfH + "^");
                        op.gravity("center");
                        op.extent(halfW, halfH);
                    }
                    op.addRawArgs("+append"); // make bottom row
                    op.closeOperation();

                    // Stack rows vertically to final WxH
                    op.addRawArgs("-append");
                    break;
                }
            }
            op.quality(80d);
            op.addImage(newImage.toString());
            cmd.run(op);
        } catch (IM4JavaException | InterruptedException e) {
            throw new IOException(e);
        }
        LOG.debug("Time in milliseconds to scale {}: {}", newImage, System.currentTimeMillis() - startTime);
    }

}
