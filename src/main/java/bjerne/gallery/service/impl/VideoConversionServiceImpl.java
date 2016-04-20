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
package bjerne.gallery.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import bjerne.gallery.service.VideoConversionService;

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
public class VideoConversionServiceImpl implements VideoConversionService {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private Map<String, String> conversionModes;

    private int maxWaitTimeSeconds = 1000;

    private String externalBinaryPath;

    private Set<Thread> activeThreads = new HashSet<>();

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
     * @param conversionModes A map of conversion modes.
     */
    @Required
    public void setConversionModes(Map<String, String> conversionModes) {
        this.conversionModes = conversionModes;
    }

    public void setMaxWaitTimeSeconds(int maxWaitTimeSeconds) {
        this.maxWaitTimeSeconds = maxWaitTimeSeconds;
    }

    public void setExternalBinaryPath(String externalBinaryPath) {
        this.externalBinaryPath = externalBinaryPath;
    }

    @Override
    public Collection<String> getAvailableVideoModes() {
        Set<String> conversionModeNames = conversionModes != null ? new HashSet<>(conversionModes.keySet()) : Collections.emptySet();
        LOG.debug("Returning conversion modes: {}", conversionModeNames);
        return conversionModeNames;
    }

    @Override
    public void convertVideo(File origVideo, File newVideo, String conversionMode) throws IOException {
        long startTime = System.currentTimeMillis();
        if (newVideo.exists()) {
            LOG.debug("{} already exists. Trying to delete.", newVideo);
            newVideo.delete();
        }
        if (!newVideo.getParentFile().exists()) {
            boolean dirsCreated = newVideo.getParentFile().mkdirs();
            if (!dirsCreated) {
                String errorMessage = String.format("Could not create all dirs for %s", newVideo);
                LOG.error(errorMessage);
                throw new IOException(errorMessage);
            }
        }
        ProcessBuilder pb = new ProcessBuilder(generateCommandParamList(origVideo, newVideo, conversionMode));
        Process pr = null;
        Thread currentThread = Thread.currentThread();
        try {
            LOG.debug("Adding current thread: {}", currentThread);
            registerThread(currentThread);
            pr = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            while (reader.ready()) {
                Thread.sleep(100);
                LOG.info("One line: {}", reader.readLine());
            }
            boolean waitResult = pr.waitFor(maxWaitTimeSeconds, TimeUnit.SECONDS);
            if (!waitResult) {
                String errorMessage = String.format("Waiting for video conversion exceeded maximum threshold of %s seconds",
                        maxWaitTimeSeconds);
                LOG.error(errorMessage);
                cleanupFailure(pr, newVideo);
                throw new IOException(errorMessage);
            }
            long duration = System.currentTimeMillis() - startTime;
            LOG.debug("Time in milliseconds to resize {}: {}", newVideo.toString(), duration);
        } catch (InterruptedException ie) {
            cleanupFailure(pr, newVideo);
            LOG.error("Was interrupted while waiting for conversion. Throwing IOException");
            throw new IOException(ie);
        } finally {
            unregisterThread(currentThread);
        }
    }

    private List<String> generateCommandParamList(File origVideo, File newVideo, String conversionMode) throws IOException {
        String commandTemplate = conversionModes.get(conversionMode);
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
