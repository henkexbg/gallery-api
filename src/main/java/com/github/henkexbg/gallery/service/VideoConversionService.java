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
package com.github.henkexbg.gallery.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;


import com.github.henkexbg.gallery.util.GalleryFileUtils;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts a video to a set of predefined formats.
 * <p>
 * While reasonably generic, this class has been written with ffmpeg or avconv in mind, and it has been tested successfully with both.
 * Effort has been made in order to allow a nice exit if the exit occurs during a conversion to not leave any threads or external processes
 * hanging if possible.
 *
 * @author Henrik Bjerne
 *
 */
@Component("videoConversionService")
public class VideoConversionService {

    final Logger LOG = LoggerFactory.getLogger(getClass());

    /**
     * This sets a map of conversion modes. The name will be the name of the video mode, while the value will be a kind of command template.
     * Two things will be done to this command template:<br> 1: three strings will be inserted, as per String.format<br> 2: there will be a
     * split on ',' on the string, assuming everything between two commas is an argument.<br> The strings that are added are: binary, input
     * file (full path), output file (full path).
     * <br>
     * EXAMPLE; The following command template works for avconv: %s,-i,%s,-strict,experimental,%s
     *
     */
    @Resource
    Map<String, String> videoConversionModes;

    @Value("${gallery.videoConversion.maxWaitTimeSeconds}")
    int maxWaitTimeSeconds = 1000;

    @Value("${gallery.videoConversion.binary}")
    String externalBinaryPath;

    @Value("${gallery.videoConversion.imageCommandTemplate}")
    String imageCommandTemplate;

    @Value("${gallery.videoConversion.externalProcessErrorLogFile}")
    String externalProcessErrorLogFile;

    @Value("${gallery.resizeDir}")
    File resizeDir;

    private final Set<Thread> activeThreads = new HashSet<>();

    /**
     * Retrieves a converted video given an original video file and a
     * @param originalVideo Original video file
     * @param videoMode Video mode
     * @return The converted video
     * @throws IOException If file is not a valid video or converted video cannot be found/generated
     */
    public File getConvertedVideo(File originalVideo, String videoMode) throws IOException {
        validateVideoFile(originalVideo);
        if (StringUtils.isEmpty(videoMode) || !videoConversionModes.containsKey(videoMode)) {
            throw new IOException("videoMode %s not defined!".formatted(videoMode));
        }
        File convertedVideo = determineConvertedVideoFilename(originalVideo, videoMode);
        LOG.debug("Converted video filename: {}", convertedVideo);
        if (!convertedVideo.exists()) {
            LOG.debug("Resized file did not exist.");
            if (!originalVideo.exists()) {
                String errorMessage = String.format("Main video %s did not exist. Could not resize.", convertedVideo.getCanonicalPath());
                LOG.error(errorMessage);
                throw new FileNotFoundException(errorMessage);
            }
            convertVideo(originalVideo);
        }
        return convertedVideo;
    }

    /**
     * Converts a video to all configured video conversion modes.
     *
     * @param originalVideo Original video.
     * @throws IOException If video cannot be converted to any of the formats
     */
    public void convertVideo(File originalVideo) throws IOException {
        validateVideoFile(originalVideo);
        for (Entry<String, String> conversionModeEntry : videoConversionModes.entrySet()) {
            File newVideo = determineConvertedVideoFilename(originalVideo, conversionModeEntry.getKey());
            if (!newVideo.exists()) {
                List<String> commandParams = generateCommandParamList(originalVideo, newVideo, conversionModeEntry.getValue());
                executeCommand(newVideo, commandParams);
            }
        }
    }

    /**
     * Generates an image for the video, to be used for example as a thumbnail.
     *
     * @param originalVideo  Video
     * @param image  Name of image to generate. The file should not exist. If it does it is ok for the implementation to remove it.
     * @param width  Width of image to be generated
     * @param height Height of image to be generated
     * @throws IOException If image cannot be generated.
     */
    public void generateImageForVideo(File originalVideo, File image, int width, int height) throws IOException {
        validateVideoFile(originalVideo);
        List<String> commandParams = generateCommandParamListForImageGeneration(originalVideo, image, width, height);
        executeCommand(image, commandParams);
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

    void validateVideoFile(File videoFile) throws IOException {
        if (videoFile == null || !videoFile.exists() || !GalleryFileUtils.isVideo(videoFile)) {
            throw new IOException("File %s is not a video file".formatted(videoFile));
        }
    }

    /**
     * Generates the filename for a converted video and creates a file object (does not perform any file operation) given an original video
     * file and its rescaling parameters.
     *
     * @param originalFile Video.
     * @param videoMode    Video mode
     * @return A {@link File} object for the converted video
     */
    File determineConvertedVideoFilename(File originalFile, String videoMode) throws IOException {
        return new File(resizeDir, File.separator + videoMode + File.separator + GalleryFileUtils.escapeFilePath(originalFile));
    }

    /**
     * Executes a command. The actual command has already been configured and is passed via the processParams parameter. These will be
     * passed to a {@link ProcessBuilder} that takes care of the execution. This is not a generic method and it assumes that a file is to be
     * generated to the given newFile parameter.
     *
     * @param newFile       New file to be generated
     * @param processParams List of process parameters.
     * @throws IOException If new file cannot be processed.
     */
    void executeCommand(File newFile, List<String> processParams) throws IOException {
        long startTime = System.currentTimeMillis();
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

    List<String> generateCommandParamList(File origVideo, File newVideo, String commandTemplate) throws IOException {
        if (StringUtils.isBlank(commandTemplate)) {
            throw new IOException("Empty command template");
        }
        String command = String.format(commandTemplate, externalBinaryPath, origVideo.getCanonicalPath(), newVideo.getCanonicalPath());
        String[] commandParams = command.split(",");
        List<String> commandParamsList = Arrays.asList(commandParams);
        LOG.debug("Command params: {}", commandParamsList);
        return commandParamsList;
    }

    List<String> generateCommandParamListForImageGeneration(File video, File newImage, int width, int height) throws IOException {
        String command =
                String.format(imageCommandTemplate, externalBinaryPath, video.getCanonicalPath(), newImage.getCanonicalPath(), width,
                        height);
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
        FileUtils.deleteQuietly(newVideo);
    }

}
