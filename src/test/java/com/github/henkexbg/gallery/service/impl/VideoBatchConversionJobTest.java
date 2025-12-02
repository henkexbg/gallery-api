package com.github.henkexbg.gallery.service.impl;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GallerySearchService;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.*;

import static org.mockito.Mockito.when;

public class VideoBatchConversionJobTest {

    private static final File TEST_ORIGINAL_DIR = new File("C:/temp/");

    private static final File TEST_VIDEO = new File(TEST_ORIGINAL_DIR, "test.mp4");

    private static final String TEST_VIDEO_PUBLIC_PATH = "TEST-PUBLIC-PATH";

    private static final String BLACKLISTED_VIDEOS_PATH = "C:/temp/blacklisted-videos.txt";

    private static final File BLACKLISTED_VIDEOS_FILE = new File(BLACKLISTED_VIDEOS_PATH);

    private VideoBatchConversionJob videoBatchConversionJob;

    private List<String> videoModes;

    private List<File> videoFiles;

    @Mock
    private GalleryAuthorizationService galleryAuthorizationService;

    @Mock
    GallerySearchService gallerySearchService;

    @Before
    public void betweenTests() throws Exception {
        MockitoAnnotations.initMocks(this);

        videoBatchConversionJob = new VideoBatchConversionJob();
        videoBatchConversionJob.blacklistedVideosFilePath = BLACKLISTED_VIDEOS_PATH;
        videoBatchConversionJob.galleryAuthorizationService = galleryAuthorizationService;
        videoBatchConversionJob.gallerySearchService = gallerySearchService;

        videoModes = new ArrayList<>();
        videoModes.add("COMPACT");

        videoFiles = List.of(TEST_VIDEO);
        when(gallerySearchService.findAllVideos()).thenReturn(videoFiles);

        if (BLACKLISTED_VIDEOS_FILE.exists()) {
            BLACKLISTED_VIDEOS_FILE.delete();
        }
        BLACKLISTED_VIDEOS_FILE.createNewFile();

    }

    @Test
    @Ignore
    public void testGetGoodVideo() throws Exception {
//        when(video.getVideo(TEST_VIDEO_PUBLIC_PATH, videoModes.get(0))).thenReturn(testGalleryFile);

        videoBatchConversionJob.runBatchJob();

        Collection<String> blacklistedVideos = FileUtils.readLines(BLACKLISTED_VIDEOS_FILE, "UTF-8");
        Assert.assertTrue("File was wrongly blacklisted", blacklistedVideos.isEmpty());
    }

    @Test
    @Ignore
    public void testGetBadVideo() throws Exception {
        videoBatchConversionJob.runBatchJob();

        List<String> blacklistedVideos = FileUtils.readLines(BLACKLISTED_VIDEOS_FILE, "UTF-8");
        Assert.assertTrue("Bad file was not blacklisted", blacklistedVideos.size() == 1);
//        Assert.assertEquals("Blacklisted file did not have proper path", testGalleryFile.getActualFile().getCanonicalPath(), blacklistedVideos.get(0));
    }

    @Test
    @Ignore
    public void testBlacklistedVideoSkipped() throws Exception {
        List<String> blacklistedVideoPaths = new ArrayList<>();
        blacklistedVideoPaths.add(TEST_VIDEO.getCanonicalPath());
        FileUtils.writeLines(BLACKLISTED_VIDEOS_FILE, blacklistedVideoPaths);

        videoBatchConversionJob.runBatchJob();
    }

}
