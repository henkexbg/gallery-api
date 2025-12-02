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
 */
package com.github.henkexbg.gallery.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import com.github.henkexbg.gallery.service.GallerySearchService;
import com.github.henkexbg.gallery.service.VideoConversionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Batch conversion job of videos. This was introduced because computers are
 * still too slow to convert videos in real-time (at least my computers!). This
 * job can then run at intervals, and convert the videos to all available video
 * modes, without having to wait for an end user request.
 *
 * @author Henrik Bjerne
 *
 */
@Component
public class VideoBatchConversionJob {

    private static final String BLACK_LISTED_VIDEOS_FILE_ENCODING = "UTF-8";

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    GallerySearchService gallerySearchService;

    @Resource
    VideoConversionService videoConversionService;

    @Resource
    GalleryAuthorizationService galleryAuthorizationService;

    @Value("${gallery.videoConversion.blacklistedVideosFile}")
    String blacklistedVideosFilePath;

    int initialDelaySeconds = 10;

    int waitPeriodSeconds = 120;

    ScheduledExecutorService executor;

    private volatile boolean running = false;

    private volatile boolean abort = false;

    @PostConstruct
    public void startBatchService() {
        executor = Executors.newScheduledThreadPool(1);
        Runnable batchJob = this::runBatchJob;
        executor.scheduleAtFixedRate(batchJob, initialDelaySeconds, waitPeriodSeconds, TimeUnit.SECONDS);
    }

    public void runBatchJob() {
        synchronized (this) {
            if (running) {
                LOG.debug("Batch job already running. Exiting");
                return;
            }
            running = true;
        }
        LOG.debug("Starting batch job for video conversion");
        long totalStartTime = System.currentTimeMillis();
        try {
            galleryAuthorizationService.loginAdminUser();
            List<File> allVideos = gallerySearchService.findAllVideos();
            LOG.debug("Found {} videos for conversion", allVideos.size());
            Collection<String> blacklistedVideoPaths = getBlacklistedVideoPaths();
            for (File oneVideoFile : allVideos) {
                if (abort) {
                    LOG.warn("Abort requested. Skipping remainder of conversions.");
                    return;
                }
                String oneVideoFilePath = null;
                try {
                    oneVideoFilePath = oneVideoFile.getCanonicalPath();
                    if (blacklistedVideoPaths.contains(oneVideoFilePath)) {
                        LOG.debug("Ignoring blacklisted video {}", oneVideoFilePath);
                        continue;
                    }
                    // We just call getConvertedVideo, as we know this will trigger a conversion if the file does not already exist
                    videoConversionService.convertVideo(oneVideoFile);
                } catch (IOException e) {
                    LOG.error("Error while converting {}. Continuing with next video.", oneVideoFile, e);
                    addBlacklistedVideo(oneVideoFilePath);
                }
            }
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            LOG.debug("Total duration of conversion batch job: {}", totalDuration);
        } catch (Exception e) {
            LOG.error("Error while running batch job", e);
        } finally {
            galleryAuthorizationService.logoutAdminUser();
            running = false;
        }
    }

    private List<String> getBlacklistedVideoPaths() {
        File blacklistedVideosFile;
        if (StringUtils.isNotBlank(blacklistedVideosFilePath) && (blacklistedVideosFile = new File(blacklistedVideosFilePath)).exists()) {
            try {
                return FileUtils.readLines(blacklistedVideosFile, BLACK_LISTED_VIDEOS_FILE_ENCODING);
            } catch (IOException ioe) {
                LOG.error("Error when retrieving list of blacklisted videos: {}. Returning empty list", blacklistedVideosFile, ioe);
            }
        }
        return Collections.emptyList();
    }

    private void addBlacklistedVideo(String videoPath) {
        if (StringUtils.isNotBlank(blacklistedVideosFilePath)) {
            try {
                File blacklistedVideosFile = new File(blacklistedVideosFilePath);
                LOG.info("Blacklisting video {}, that failed during conversion", videoPath);
                FileUtils.write(blacklistedVideosFile, videoPath + System.lineSeparator(), BLACK_LISTED_VIDEOS_FILE_ENCODING, true);
            } catch (IOException ioe) {
                LOG.error("Error when adding {} as blacklisted video. Skipping.", videoPath, ioe);
            }
        }
    }

    @PreDestroy
    public void stopBatchJob() {
        abort = true;
        executor.shutdown();
    }

}
