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
package com.github.henkexbg.gallery.service.impl;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.github.henkexbg.gallery.service.ImageResizeService;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tests in this class are ignored per default. They work, but require
 * instance-specific configuration and can not be enabled per default
 * 
 * @author Henrik
 *
 */
public class ImageResizeServiceTest {

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private String imageMagickPath = "/usr/bin/convert";

	private String[] allowedSuffixes = new String[] { "jpg" };

	private File inputFilesDir = new File("/home/henrik/dev/gallery-test-data/im-test");

	private File outputDir = new File("/home/henrik/dev/gallery-test-data/im-test/testfilesoutput/");

	private int outputResWidth = 1920;

	private int outputResHeight = 1080;

	@Test
	@Ignore
	public void testGenerateCompositeImage() throws Exception {
		Collection<File> fileCollection = (Collection<File>) FileUtils.listFiles(inputFilesDir, allowedSuffixes, false);
		List<File> fileList = new ArrayList<>(fileCollection);
		ImageResizeService imageResizeServiceNative = new ImageResizeServiceIMImpl();
		imageResizeServiceNative.generateCompositeImage(fileList,
				new File(outputDir.toString() + File.separator + "composite" + System.currentTimeMillis() + ".jpg"),
				outputResWidth, outputResHeight);
	}

	@Test
	@Ignore
	public void testGenerateCompositeImageTwoImages() throws Exception {
		Collection<File> fileCollection = (Collection<File>) FileUtils.listFiles(inputFilesDir, allowedSuffixes, false);
		List<File> fileList = new ArrayList<>(fileCollection);
		ImageResizeService imageResizeServiceNative = new ImageResizeServiceImpl();
		imageResizeServiceNative.generateCompositeImage(fileList.subList(0, 2),
				new File(outputDir.toString() + File.separator + "composite" + System.currentTimeMillis() + ".jpg"),
				outputResWidth, outputResHeight);
	}

	@Test
	@Ignore
	public void testPerformanceNative() {
		ImageResizeService imageResizeServiceNative = new ImageResizeServiceImpl();
		testPerformance(imageResizeServiceNative);
	}

	@Test
	@Ignore
	public void testPerformanceIM() {
		ImageResizeService imageResizeServiceIM = new ImageResizeServiceIMImpl();
		//((ImageResizeServiceIMImpl) imageResizeServiceIM).setImageMagickPath(imageMagickPath);
		testPerformance(imageResizeServiceIM);
	}

	private void testPerformance(ImageResizeService imageResizeService) {
		long startTime = System.currentTimeMillis();
		Collection<File> fileCollection = (Collection<File>) FileUtils.listFiles(inputFilesDir, allowedSuffixes, false);
		fileCollection.parallelStream().forEach(f -> {
			wrapResizeNoException(imageResizeService, f, outputResWidth, outputResHeight);
		});
		long duration = System.currentTimeMillis() - startTime;
		LOG.info("DURATION: {}", duration);
	}

	private void wrapResizeNoException(ImageResizeService imageResizeService, File origImage, int width, int height) {
		try {
			imageResizeService.resizeImage(origImage,
					new File(outputDir.toString() + File.separator + origImage.getName()), width, height);
		} catch (IOException ioe) {
			fail();
		}
	}

}
