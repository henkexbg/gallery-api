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
package com.github.henkexbg.gallery.job;

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
import java.util.*;
import java.util.stream.Collectors;

import com.github.henkexbg.gallery.job.listener.FileChangeListener;
import com.github.henkexbg.gallery.job.listener.GalleryRootDirChangeListener;
import com.github.henkexbg.gallery.service.Deduplicator;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.bean.GalleryRootDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Job rather than a service. It listens continuously to changes to the given
 * config file. Any time a change is noticed, the file is parsed, and the new
 * root dirs are loaded. All listeners are then notified.
 * 
 * @author Henrik Bjerne
 *
 */
@Component
public class GalleryRootDirConfigJob {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    Collection<FileChangeListener> fileChangeListeners;

    @Resource
    Collection<GalleryRootDirChangeListener> galleryRootDirChangeListeners;

    @Value("${gallery.groupDirAuth.propertiesFile}")
    File configFile;

    private WatchService watcher;
    private DirectoryWatcher directoryWatcher = null;
    private Deduplicator deduplicator;

    @PostConstruct
    public void setUp() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        // Initial loading
        updateConfigFromFile();

        // Kick of watcher thread
        Runnable fileWatcher = this::watchForChanges;
        new Thread(fileWatcher).start();

        LOG.info("Creating Deduplicator");
        deduplicator = new Deduplicator();
        deduplicator.addListener((paths) -> {
            Set<File> files = paths.stream().map(Path::toFile).collect(Collectors.toCollection(HashSet::new));
            notifyFileChangeListeners(files, Set.of());
        });
    }

    @PreDestroy
    public void shutdown() {
        try {
            watcher.close();
        } catch (IOException ioe) {
            LOG.info("Exception while closing watcher", ioe);
        }
        if (deduplicator != null) {
            deduplicator.stop();
        }
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
        handleUpdatedRootDirs(newRootDirs);
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

    private synchronized void handleUpdatedRootDirs(Collection<GalleryRootDir> galleryRootDirs) {
        LOG.debug("onGalleryRootDirsUpdated(galleryRootDirs: {}", galleryRootDirs);
        notifyGalleryRootDirChangeListeners(galleryRootDirs);
        List<Path> rootDirs = galleryRootDirs.stream().map(grd -> grd.getDir().toPath()).toList();
        LOG.debug("Found {} directories to watch for search service", rootDirs.size());
        try {
            if (directoryWatcher != null) {
                directoryWatcher.close();
            }
            directoryWatcher = DirectoryWatcher.builder().paths(rootDirs) // or use paths(directoriesToWatch)
                    .listener(event -> {
                        switch (event.eventType()) {
                            case CREATE, MODIFY:
                                // Run via deduplicator to remove duplicates. Not required for DELETE
                                deduplicator.add(event.path());
                                break;
                            case DELETE:
                                notifyFileChangeListeners(Collections.emptySet(), Set.of(event.path().toFile()));
                                break;
                        }
                    }).fileHashing(false)
                    .build();
            LOG.debug("Built directory watcher for search service. About to start watching");
            directoryWatcher.watchAsync();
        } catch (IOException ioe) {
            LOG.error("Exception while reading gallery root directories for DB indexing", ioe);
        }
        LOG.debug("Done adding directory watcher for search service");
    }

    void notifyFileChangeListeners(Set<File> upsertedFiles, Set<File> deletedFiles) {
        fileChangeListeners.forEach(r -> {
            try {
                r.onFilesUpdated(upsertedFiles, deletedFiles);
            } catch (Exception e) {
                LOG.error("Exception while notifying file listeners", e);
            }
        });
        LOG.debug("Done notifying fileChangeListeners");
    }

    void notifyGalleryRootDirChangeListeners(Collection<GalleryRootDir> rootDirs) {
        galleryRootDirChangeListeners.forEach(r -> {
            try {
            r.onGalleryRootDirsUpdated(rootDirs);
            } catch (Exception e) {
                LOG.error("Exception while notifying gallery root dir listeners", e);
            }

        });
        LOG.debug("Done notifying galleryRootDirChangeListeners");
    }

}
