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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.service.VideoConversionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementation of the {@link VideoConversionService}. This class assumes that
 * an external executable will be called. While reasonably generic, this class
 * has been written with ffmpeg or avconv in mind, and it has been tested
 * successfully with both. A bit of effort has been made in order to allow a
 * nice exit if the exit occurs during a conversion to not leave any threads or
 * external processes hanging if possible.
 *
 * @author Henrik Bjerne
 *
 */
@Component("videoConversionService")
public class VideoConversionServiceImpl implements VideoConversionService {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    private Map<String, String> videoConversionModes;

    @Value("${gallery.videoConversion.maxWaitTimeSeconds}")
    private int maxWaitTimeSeconds = 1000;

    @Value("${gallery.videoConversion.binary}")
    private String externalBinaryPath;

    @Value("${gallery.videoConversion.imageCommandTemplate}")
    private String imageCommandTemplate;

    @Value("${gallery.videoConversion.externalProcessErrorLogFile}")
    private String externalProcessErrorLogFile;

    private final Set<Thread> activeThreads = new HashSet<>();

    /**
     * This sets a map of conversion modes. The name will be the name of the
     * video mode, while the value will be a kind of command template. Two
     * things will be done to this command template:<br>
     * 1: three strings will be inserted, as per String.format<br>
     * 2: there will be a split on ',' on the string, assuming everything between two commas is an argument.<br>
     * The strings that are added are: binary, input file (full path), output file (full path).
     * <br>
     * EXAMPLE; The following command template works for avconv: %s,-i,%s,-strict,experimental,%s
     *
     * @param videoConversionModes A map of conversion modes.
     */
    public void setVideoConversionModes(Map<String, String> videoConversionModes) {
        this.videoConversionModes = videoConversionModes;
    }

    public void setMaxWaitTimeSeconds(int maxWaitTimeSeconds) {
        this.maxWaitTimeSeconds = maxWaitTimeSeconds;
    }

    public void setExternalBinaryPath(String externalBinaryPath) {
        this.externalBinaryPath = externalBinaryPath;
    }

    public void setImageCommandTemplate(String imageCommandTemplate) {
        this.imageCommandTemplate = imageCommandTemplate;
    }

    public void setExternalProcessErrorLogFile(String externalProcessErrorLogFile) {
        this.externalProcessErrorLogFile = externalProcessErrorLogFile;
    }

    @Override
    public Collection<String> getAvailableVideoModes() {
        Set<String> conversionModeNames = videoConversionModes != null ? new HashSet<>(videoConversionModes.keySet()) : Collections.emptySet();
        LOG.debug("Returning conversion modes: {}", conversionModeNames);
        return conversionModeNames;
    }

    @Override
    public void convertVideo(File origVideo, File newVideo, String conversionMode) throws IOException {
        List<String> commandParams = generateCommandParamList(origVideo, newVideo, conversionMode);
        executeCommand(newVideo, commandParams);
    }

    @Override
    public void generateImageForVideo(File video, File image, int width, int height) throws IOException {
        List<String> commandParams = generateCommandParamListForImageGeneration(video, image, width, height);
        executeCommand(image, commandParams);
    }

    /**
     * Executes a command. The actual command has already been configured and
     * is passed via the processParams parameter. These will be passed to a
     * {@link ProcessBuilder} that takes care of the execution. This is not a
     * generic method and it assumes that a file is to be generated to the
     * given newFile parameter.
     * @param newFile New file to be generated
     * @param processParams List of process parameters.
     * @throws IOException If new file cannot be processed.
     */
    private void executeCommand(File newFile, List<String> processParams) throws IOException {
        long startTime = System.currentTimeMillis();
        if (newFile.exists()) {
            LOG.debug("{} already exists. Trying to delete.", newFile);
            newFile.delete();
        }
        if (!newFile.getParentFile().exists()) {
            boolean dirsCreated = newFile.getParentFile().mkdirs();
            if (!dirsCreated) {
                String errorMessage = String.format("Could not create all dirs for %s", newFile);
                LOG.error(errorMessage);
                throw new IOException(errorMessage);
            }
        }
        ProcessBuilder pb = new ProcessBuilder(processParams);
        Process pr = null;
        Thread currentThread = Thread.currentThread();
        try {
            LOG.debug("Adding current thread: {}", currentThread);
            registerThread(currentThread);
            if (StringUtils.isNotBlank(externalProcessErrorLogFile)) {
                LOG.debug("Will log external process error output to {}", externalProcessErrorLogFile);
                pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(externalProcessErrorLogFile)));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            }
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pr = pb.start();

            boolean waitResult = pr.waitFor(maxWaitTimeSeconds, TimeUnit.SECONDS);
            if (!waitResult) {
                String errorMessage = String.format("Waiting for video conversion exceeded maximum threshold of %s seconds",
                        maxWaitTimeSeconds);
                LOG.error(errorMessage);
                cleanupFailure(pr, newFile);
                throw new IOException(errorMessage);
            }
            if (pr.exitValue() != 0) {
                String errorMessage = String.format("Error when generating new file %s. Cleaning up.", newFile.getCanonicalPath());
                LOG.error(errorMessage);
                cleanupFailure(pr, newFile);
                throw new IOException(errorMessage);
            }
            long duration = System.currentTimeMillis() - startTime;
            LOG.debug("Time in milliseconds to generate {}: {}", newFile, duration);
        } catch (InterruptedException ie) {
            cleanupFailure(pr, newFile);
            LOG.error("Was interrupted while waiting for conversion. Throwing IOException");
            throw new IOException(ie);
        } finally {
            unregisterThread(currentThread);
        }
    }

    private List<String> generateCommandParamList(File origVideo, File newVideo, String conversionMode) throws IOException {
        String commandTemplate = videoConversionModes.get(conversionMode);
        if (StringUtils.isBlank(commandTemplate)) {
            String errorMessage = String.format("Unknown conversion mode %s", commandTemplate);
            throw new IOException(errorMessage);
        }
        String command = String.format(commandTemplate, externalBinaryPath, origVideo.getCanonicalPath(), newVideo.getCanonicalPath());
        String[] commandParams = command.split(",");
        List<String> commandParamsList = Arrays.asList(commandParams);
        LOG.debug("Command params: {}", commandParamsList);
        return commandParamsList;
    }

    private List<String> generateCommandParamListForImageGeneration(File video, File newImage, int width, int height) throws IOException {
        String command = String.format(imageCommandTemplate, externalBinaryPath, video.getCanonicalPath(), newImage.getCanonicalPath(), width, height);
        String[] commandParams = command.split(",");
        List<String> commandParamsList = Arrays.asList(commandParams);
        LOG.debug("Command params: {}", commandParamsList);
        return commandParamsList;
    }

    private synchronized void registerThread(Thread thread) {
        activeThreads.add(thread);
    }

    private synchronized void unregisterThread(Thread thread) {
        activeThreads.remove(thread);
        if (activeThreads.isEmpty()) {
            notify();
        }
    }

    private void cleanupFailure(Process pr, File newVideo) {
        LOG.debug("Cleaning up failing conversion job. Killing process {}", pr);
        pr.destroy();
        LOG.debug("Trying to remove new file (if any): {}", newVideo.toString());
        newVideo.delete();
    }

    @PreDestroy
    public synchronized void shutdown() {
        LOG.info("Shutdown called. Number of currently active processes: {}", activeThreads.size());
        for (Thread oneThread : activeThreads) {
            oneThread.interrupt();
        }
        try {
            wait(5000);
        } catch (InterruptedException ie) {
        }
    }

}
