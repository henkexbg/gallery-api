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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

/**
 * Batch conversion job of videos. This was introduced because computers are
 * still too slow to convert videos in real-time (at least my computers!). This
 * job can then run at intervals, and convert the videos to all available video
 * modes, without having to wait for an end user request.
 * 
 * @author Henrik Bjerne
 *
 */
public class VideoBatchConversionJob {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private GalleryService galleryService;

    private GalleryAuthorizationService galleryAuthorizationService;

    private int initialDelaySeconds = 10;

    private int waitPeriodSeconds = 120;

    private boolean running = false;

    private boolean abort = false;

    private ScheduledExecutorService executor;

    @Required
    public void setGalleryService(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @Required
    public void setGalleryAuthorizationService(GalleryAuthorizationService galleryAuthorizationService) {
        this.galleryAuthorizationService = galleryAuthorizationService;
    }

    public void setInitialDelaySeconds(int initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
    }

    public void setWaitPeriodSeconds(int waitPeriodSeconds) {
        this.waitPeriodSeconds = waitPeriodSeconds;
    }

    @PostConstruct
    public void startBatchService() {
        executor = Executors.newScheduledThreadPool(1);
        Runnable batchJob = () -> runBatchJob();
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
            List<GalleryFile> allVideos = galleryService.getAllVideos();
            LOG.debug("Found {} videos for conversion", allVideos.size());
            allVideos.forEach(v -> LOG.debug("one video: {}", v.getPublicPath()));
            Collection<String> videoConversionModes = galleryService.getAvailableVideoModes();
            LOG.debug("Found the following video conversion modes: {}", videoConversionModes);
            for (GalleryFile oneGalleryFile : allVideos) {
                for (String oneConversionMode : videoConversionModes) {
                    if (abort) {
                        LOG.warn("Abort requested. Skipping remainder of conversions.");
                        return;
                    }
                    try {
                        // We just call getVideo, as we know this will trigger a
                        // conversion if the converted file does not already
                        // exist.
                        galleryService.getVideo(oneGalleryFile.getPublicPath(), oneConversionMode);
                    } catch (IOException | NotAllowedException e) {
                        LOG.error("Error while converting {} for format {}. Continuing with next video.", oneGalleryFile.getPublicPath(),
                                oneConversionMode);
                    }
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

    @PreDestroy
    public void stopBatchJob() {
        abort = true;
        executor.shutdown();
    }

}
