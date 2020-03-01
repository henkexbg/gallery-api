package com.github.henkexbg.gallery.service.impl;

import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryService;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class VideoBatchConversionJobTest {

    private static final File TEST_ORIGINAL_DIR = new File("C:/temp/");

    private static final File TEST_VIDEO = new File(TEST_ORIGINAL_DIR, "test.mp4");

    private static final String TEST_VIDEO_PUBLIC_PATH = "TEST-PUBLIC-PATH";

    private static final String BLACKLISTED_VIDEOS_PATH = "C:/temp/blacklisted-videos.txt";

    private static final File BLACKLISTED_VIDEOS_FILE = new File(BLACKLISTED_VIDEOS_PATH);

    private VideoBatchConversionJob videoBatchConversionJob;

    private List<String> videoModes;

    private List<GalleryFile> galleryFiles;

    private GalleryFile testGalleryFile;

    @Mock
    private GalleryAuthorizationService galleryAuthorizationService;

    @Mock
    private GalleryService galleryService;

    @Mock
    private Map<String, File> rootPaths;

    @Before
    public void betweenTests() throws Exception {
        MockitoAnnotations.initMocks(this);

        videoBatchConversionJob = new VideoBatchConversionJob();
        videoBatchConversionJob.setBlacklistedVideosFile(BLACKLISTED_VIDEOS_PATH);
        videoBatchConversionJob.setGalleryAuthorizationService(galleryAuthorizationService);
        videoBatchConversionJob.setGalleryService(galleryService);

        videoModes = new ArrayList<>();
        videoModes.add("COMPACT");
        when(galleryService.getAvailableVideoModes()).thenReturn(videoModes);

        galleryFiles = new ArrayList<>();
        when(galleryService.getAllVideos()).thenReturn(galleryFiles);

        testGalleryFile = new GalleryFile();
        testGalleryFile.setActualFile(TEST_VIDEO);
        testGalleryFile.setPublicPath(TEST_VIDEO_PUBLIC_PATH);
        galleryFiles.add(testGalleryFile);

        if (BLACKLISTED_VIDEOS_FILE.exists()) {
            BLACKLISTED_VIDEOS_FILE.delete();
        }
        BLACKLISTED_VIDEOS_FILE.createNewFile();

    }

    @Test
    public void testGetGoodVideo() throws Exception {
        when(galleryService.getVideo(TEST_VIDEO_PUBLIC_PATH, videoModes.get(0))).thenReturn(testGalleryFile);

        videoBatchConversionJob.runBatchJob();

        Collection<String> blacklistedVideos = FileUtils.readLines(BLACKLISTED_VIDEOS_FILE, "UTF-8");
        Assert.assertTrue("File was wrongly blacklisted", blacklistedVideos.isEmpty());
    }

    @Test
    public void testGetBadVideo() throws Exception {
        when(galleryService.getVideo(TEST_VIDEO_PUBLIC_PATH, videoModes.get(0))).thenThrow(new IOException());

        videoBatchConversionJob.runBatchJob();

        List<String> blacklistedVideos = FileUtils.readLines(BLACKLISTED_VIDEOS_FILE, "UTF-8");
        Assert.assertTrue("Bad file was not blacklisted", blacklistedVideos.size() == 1);
        Assert.assertEquals("Blacklisted file did not have proper path", testGalleryFile.getActualFile().getCanonicalPath(), blacklistedVideos.get(0));
    }

    @Test
    public void testBlacklistedVideoSkipped() throws Exception {
        List<String> blacklistedVideoPaths = new ArrayList<>();
        blacklistedVideoPaths.add(TEST_VIDEO.getCanonicalPath());
        FileUtils.writeLines(BLACKLISTED_VIDEOS_FILE, blacklistedVideoPaths);

        videoBatchConversionJob.runBatchJob();

        Mockito.verify(galleryService, never()).getVideo(TEST_VIDEO_PUBLIC_PATH, videoModes.get(0));
    }

}
