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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static java.nio.file.StandardWatchEventKinds.*;

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.job.GalleryRootDirChangeListener;
import com.github.henkexbg.gallery.bean.GalleryRootDir;

/**
 * Job rather than a service. It listens continuously to changes to the given
 * config file. Any time a change is noticed, the file is parsed, and the new
 * root dirs are loaded. All listeners are then notified.
 * 
 * @author Henrik Bjerne
 *
 */
public class GalleryRootDirConfigJob {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private File configFile;

    private WatchService watcher;

    private Collection<GalleryRootDirChangeListener> galleryRootDirChangeListeners;

    @PostConstruct
    public void setUp() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        // Initial loading
        updateConfigFromFile();

        // Kick of watcher thread
        Runnable fileWatcher = () -> {
            watchForChanges();
        };
        new Thread(fileWatcher).start();
    }

    @PreDestroy
    public void shutdown() {
        try {
            watcher.close();
        } catch (IOException ioe) {
            LOG.info("Exception while closing watcher", ioe);
        }
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    public void setGalleryRootDirChangeListeners(Collection<GalleryRootDirChangeListener> galleryRootDirChangeListeners) {
        this.galleryRootDirChangeListeners = galleryRootDirChangeListeners;
    }

    private void updateConfigFromFile() throws IOException {
        LOG.debug("Entering updateConfigFromFile()");
        Collection<GalleryRootDir> newRootDirs = new ArrayList<>();
        Properties prop = new Properties();
        prop.load(new FileReader(configFile));
        prop.forEach((k, v) -> {
            String[] split = ((String) k).split("\\.");
            if (split.length == 2) {
                GalleryRootDir oneRootDir = new GalleryRootDir();
                newRootDirs.add(oneRootDir);
                oneRootDir.setRole(split[0]);
                oneRootDir.setName(split[1]);
                oneRootDir.setDir(new File(v.toString()));
            }
        });
        galleryRootDirChangeListeners.forEach(r -> r.onGalleryRootDirsUpdated(newRootDirs));
        LOG.debug("Done notifying listeners");
    }

    private void watchForChanges() {
        Path dir = configFile.getParentFile().toPath();
        try {
            WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            for (;;) {
                try {
                    key = watcher.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    LOG.info("Interrupted during watcher.take(). Exiting watch loop.");
                    return;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    Path child = dir.resolve(filename);
                    if (child.equals(configFile.toPath())) {
                        LOG.debug("File was changed.");
                        updateConfigFromFile();
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException ioe) {
            LOG.error("Exception in filewatcher loop. Exiting.", ioe);
        }
    }

}
