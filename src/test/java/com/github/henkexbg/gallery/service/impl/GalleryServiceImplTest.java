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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.service.ImageResizeService;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class GalleryServiceImplTest {

    private static final File RESIZE_DIR = new File("C:/temp/gallery-test");

    private static final File TEST_ORIGINAL_DIR = new File("C:/temp/root");

    private static final File TEST_ORIGINAL_IMAGE = new File(TEST_ORIGINAL_DIR, "test.jpg");

    @Mock
    private GalleryAuthorizationService galleryAuthorizationService;

    @Mock
    private ImageResizeService imageResizeService;

    @Mock
    private Map<String, File> rootPaths;

    private GalleryService galleryService;

    @Before
    public void betweenTests() throws Exception {
//        galleryService = new GalleryService();
//        galleryService.setResizeDir(RESIZE_DIR);
//        Set<String> allowedFileExtensions = new HashSet<>();
//        allowedFileExtensions.add("jpg");
//        MockitoAnnotations.initMocks(this);
//        galleryService.setGalleryAuthorizationService(galleryAuthorizationService);
//        galleryService.setImageResizeService(imageResizeService);
//        galleryService.setAllowedFileExtensions(allowedFileExtensions);
//        when(galleryAuthorizationService.getRootPathsForCurrentUser()).thenReturn(rootPaths);
//        if (!TEST_ORIGINAL_IMAGE.exists()) {
//            TEST_ORIGINAL_IMAGE.createNewFile();
//        }
    }

    @Test
    @Ignore
    public void testGetImage() throws Exception {
        when(rootPaths.get(any())).thenReturn(new File(TEST_ORIGINAL_DIR.getCanonicalPath()));
        galleryService.getImage("publicRoot/test.jpg", 20, 20);
    }
}
