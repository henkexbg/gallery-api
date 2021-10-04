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

import java.awt.Graphics2D;
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

	private static final int INPUT_STREAM_BUFFER_SIZE = 65536;

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	public ImageResizeServiceImpl() {
		// Slight performance improvement in disabling ImageIO cache
		ImageIO.setUseCache(false);
	}

	@Override
	public void resizeImage(File originalImageFile, File newImageFile, int width, int height) throws IOException {
		LOG.debug("Entering resizeImage(originalImageFile={}, newImageFile={}, width={}, height={})", originalImageFile,
				newImageFile, width, height);
		long startTime = System.currentTimeMillis();
		BufferedImage originalImage = loadImage(originalImageFile);
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
		writeImage(scaledImage, newImageFile);
		long duration = System.currentTimeMillis() - startTime;
		LOG.debug("Time in milliseconds to scale {}: {}", newImageFile.toString(), duration);
	}

	@Override
	public void generateCompositeImage(List<File> origImages, File newImageFile, int width, int height)
			throws IOException {
		LOG.debug("Entering resizeImage(origImages.size={}, newImageFile={}, width={}, height={})", origImages.size(),
				newImageFile, width, height);
		long startTime = System.currentTimeMillis();
		if (origImages.isEmpty()) {
			throw new IOException("At least one image required to build a composite image!");
		}

		BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaledImage.createGraphics();

		if (origImages.size() == 1) {
			// Using just one image. Defaulting to normal image resizing
			resizeImage(origImages.get(0), newImageFile, width, height);
		} else if (origImages.size() < 4) {
			// Using 2 images
			float soughtRatio = (float) width / ((float) height * 2);
			loadCropAndDrawImage(origImages.get(0), g, soughtRatio, 0, 0, width / 2, height);
			loadCropAndDrawImage(origImages.get(0), g, soughtRatio, width / 2, 0, width, height);
		} else {
			// Using 4 images
			float soughtRatio = (float) width / (float) height;
			loadCropAndDrawImage(origImages.get(0), g, soughtRatio, 0, 0, width / 2, height / 2);
			loadCropAndDrawImage(origImages.get(1), g, soughtRatio, width / 2, 0, width, height / 2);
			loadCropAndDrawImage(origImages.get(2), g, soughtRatio, 0, height / 2, width / 2, height);
			loadCropAndDrawImage(origImages.get(3), g, soughtRatio, width / 2, height / 2, width, height);
		}
		writeImage(scaledImage, newImageFile);
		long duration = System.currentTimeMillis() - startTime;
		LOG.debug("Time in milliseconds to scale {}: {}", newImageFile.toString(), duration);
	}

	/**
	 * Highly specialized helper method to reduce repetitive code for composite
	 * image drawing. Loads an image, crops it according to the sought ratio and
	 * then draws it onto the given Graphics2D instance. The dx1, dy1, dx2 and dy2
	 * parameters are directly forwarded to the
	 * {@link Graphics2D#drawImage(java.awt.Image, int, int, int, int, int, int, int, int, java.awt.image.ImageObserver)}
	 * call.
	 * 
	 * @param imageFile      Image file
	 * @param targetGraphics Target graphics instance on which to draw
	 * @param soughtRatio    Sought ratio of input file
	 * @param dx1            As per Graphics2D.drawImage()
	 * @param dy1            As per Graphics2D.drawImage()
	 * @param dx2            As per Graphics2D.drawImage()
	 * @param dy2            As per Graphics2D.drawImage()
	 * @throws IOException
	 */
	private void loadCropAndDrawImage(File imageFile, Graphics2D targetGraphics, float soughtRatio, int dx1, int dy1,
			int dx2, int dy2) throws IOException {
		long startMillis = System.currentTimeMillis();
		BufferedImage imageFull = loadImage(imageFile);
		long afterLoadMillis = System.currentTimeMillis();
		BufferedImage image = cropImage(imageFull, soughtRatio);
		long afterCropMillis = System.currentTimeMillis();
		targetGraphics.drawImage(image, dx1, dy1, dx2, dy2, 0, 0, image.getWidth(), image.getHeight(), null);
		long afterDrawMillis = System.currentTimeMillis();
		LOG.debug("Loaded and drew image {}. loadTime {}, cropTime: {}, drawTime: {}", imageFile,
				afterLoadMillis - startMillis, afterCropMillis - afterLoadMillis, afterDrawMillis - afterCropMillis);
	}

	/**
	 * Loads a {@link BufferedImage} given a file.
	 * 
	 * @param imageFile Image file
	 * @return a {@link BufferedImage}
	 * @throws IOException If file could not be converted to a proper BufferedImage
	 */
	private BufferedImage loadImage(File imageFile) throws IOException {
		// There seems to be a minor performance improvement in using ImageIO with an
		// inputstream and custom buffer size over calling it with a file directly
		InputStream is = new BufferedInputStream(new FileInputStream(imageFile), INPUT_STREAM_BUFFER_SIZE);
		BufferedImage image = ImageIO.read(is);
		is.close();
		if (image == null) {
			String errorMessage = String.format("File %s could not be parsed as an image",
					imageFile.getCanonicalPath());
			LOG.error(errorMessage);
			throw new IOException(errorMessage);
		}
		return image;
	}

	/**
	 * Writes a {@link BufferedImage} to a file.
	 * 
	 * @param image     {@link BufferedImage}
	 * @param imageFile File to write to.
	 * @throws IOException If file could not be written
	 */
	private void writeImage(BufferedImage image, File imageFile) throws IOException {
		OutputStream resultImageOutputStream = new BufferedOutputStream(FileUtils.openOutputStream(imageFile));
		String extension = FilenameUtils.getExtension(imageFile.getName());
		ImageIO.write(image, extension, resultImageOutputStream);
		resultImageOutputStream.flush();
		resultImageOutputStream.close();
	}

	/**
	 * Crops the given image by looking at the sought ratio (width / height) and
	 * comparing to the ratio of the image. If the sought ratio is smaller, the
	 * image is too wide and will need to be cropped on the sides. Conversely, if
	 * the sought ratio is larger, the given image will be cropped at the top an
	 * bottom.
	 * 
	 * @param image       Image
	 * @param soughtRatio Sought ratio for cropping
	 * @return A cropped image that fulfills the sought ratio of width / height.
	 *         Note: the returned image is a sub-image of the input image, which
	 *         means any changes to the input image will change the cropped image
	 *         too.
	 * @throws IOException If an exception occurs during cropping.
	 */
	public BufferedImage cropImage(BufferedImage image, float soughtRatio) throws IOException {
		int origWidth = image.getWidth();
		int origHeight = image.getHeight();
		float origRatio = (float) origWidth / (float) origHeight;
		float ratioOfRatios = (float) origRatio / (float) soughtRatio;
		int croppedWidth = ratioOfRatios > 1 ? (int) ((float) origWidth / ratioOfRatios) : origWidth;
		int startX = (origWidth - croppedWidth) / 2;
		int croppedHeight = ratioOfRatios < 1 ? (int) ((float) origHeight * ratioOfRatios) : origHeight;
		int startY = (origHeight - croppedHeight) / 2;
		image.getSubimage(startX, startY, croppedWidth, croppedHeight);
		return image;
	}

}
