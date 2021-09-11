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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.service.ImageResizeService;

/**
 * Java-only implementation of {@link ImageResizeService}. Uses AWT libraries
 * for the scaling.
 * 
 * @author Henrik Bjerne
 *
 */
public class ImageResizeServiceImpl implements ImageResizeService {

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	@Override
	public void resizeImage(File originalImageFile, File newImageFile, int width, int height) throws IOException {
		LOG.debug("Entering resizeImage(originalImageFile={}, newImageFile={}, width={}, height={})", originalImageFile,
				newImageFile, width, height);
		long startTime = System.currentTimeMillis();
		InputStream is = new BufferedInputStream(new FileInputStream(originalImageFile));
		BufferedImage originalImage = ImageIO.read(is);
		is.close();
		if (originalImage == null) {
			String errorMessage = String.format("File %s could not be parsed as an image",
					originalImageFile.getCanonicalPath());
			LOG.error(errorMessage);
			throw new IOException(errorMessage);
		}
		int origWidth = originalImage.getWidth();
		int origHeight = originalImage.getHeight();
		LOG.debug("Original size of image - width: {}, height={}", origWidth, height);
		float widthFactor = ((float) origWidth) / ((float) width);
		float heightFactor = ((float) origHeight) / ((float) height);
		float maxFactor = Math.max(widthFactor, heightFactor);
		int newHeight, newWidth;
		if (maxFactor > 1) {
			newHeight = (int) (((float) origHeight) / maxFactor);
			newWidth = (int) (((float) origWidth) / maxFactor);
		} else {
			newHeight = origHeight;
			newWidth = origWidth;
		}
		BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaledImage.createGraphics();
		AffineTransform at = AffineTransform.getScaleInstance(((float) newWidth) / ((float) origWidth),
				((float) newHeight) / ((float) origHeight));
		// g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
		// RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawRenderedImage(originalImage, at);
		LOG.debug("Size of scaled image will be: width={}, height={}", newWidth, newHeight);
		OutputStream resultImageOutputStream = new BufferedOutputStream(FileUtils.openOutputStream(newImageFile));
		String extension = FilenameUtils.getExtension(originalImageFile.getName());
		ImageIO.write(scaledImage, extension, resultImageOutputStream);
		resultImageOutputStream.flush();
		resultImageOutputStream.close();
		long duration = System.currentTimeMillis() - startTime;
		LOG.debug("Time in milliseconds to scale {}: {}", newImageFile.toString(), duration);
	}

	@Override
	public void generateCompositeImage(List<File> origImages, File newImage, int width, int height) throws IOException {
		// TODO: Use multiple images to build directory image
		resizeImage(origImages.get(0), newImage, width, height);
	}
}
