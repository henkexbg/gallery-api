package com.github.henkexbg.gallery.job;

import com.github.henkexbg.gallery.bean.GalleryRootDir;
import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.apache.commons.io.FileUtils.listFilesAndDirs;

public class GalleryFileWatcher {

    private GalleryAuthorizationService galleryAuthorizationService;

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private Collection<GalleryRootDir> rootDirs = new ArrayList<>();

    private Collection<FileChangeListener> fileChangeListeners;

    private WatchService watcher;

    public void setGalleryAuthorizationService(GalleryAuthorizationService galleryAuthorizationService) {
        this.galleryAuthorizationService = galleryAuthorizationService;
    }

    @PostConstruct
    public void setUp() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        // Initial loading
//        updateConfigFromFile();

        // Kick of watcher thread
        Runnable fileWatcher = () -> {
            watchForChanges();
        };
        new Thread(fileWatcher).start();
        ;
    }

    @PreDestroy
    public void shutdown() {
        try {
            watcher.close();
        } catch (IOException ioe) {
            LOG.info("Exception while closing watcher", ioe);
        }
    }

    public void setFileChangeListeners(Collection<FileChangeListener> fileChangeListeners) {
        this.fileChangeListeners = fileChangeListeners;
    }

    private void watchForChanges() {
        try {
            galleryAuthorizationService.loginAdminUser();
            List<File> rootDirs = galleryAuthorizationService.getRootPathsForCurrentUser().entrySet().stream().map(e -> e.getValue()).collect(Collectors.toList());
            Collection<File> allDirectories = getAllDirectories(rootDirs);
            Map<WatchKey, Path> watchKeyDirMap = new HashMap<>();
            for (File oneDir : allDirectories) {
                watchKeyDirMap.put(oneDir.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), oneDir.toPath());
            }
            WatchKey key = null;
            for (;;) {
                try {
                    key = watcher.take();
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    LOG.info("Interrupted during watcher.take(). Exiting watch loop.");
                    return;
                }
                Path directoryPath = watchKeyDirMap.get(key);
                Set<File> createdOrUpdatedFiles = new HashSet<>();
                Set<File> deletedFiles = new HashSet<>();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    Path child = directoryPath.resolve(filename);
                    File foundFile = child.toFile();
                    if (foundFile.isDirectory() && ENTRY_CREATE.equals(kind)) {
                        LOG.debug("New directory {} created. Adding to watcher.", foundFile.getCanonicalPath());
                        watchKeyDirMap.put(foundFile.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), child);

                    }
                    if (ENTRY_DELETE.equals(kind)) {
                        deletedFiles.add(foundFile);
                        Optional<WatchKey> optKey = watchKeyDirMap.entrySet().stream().filter(e -> e.getValue().equals(child)).map(e -> e.getKey()).findAny();
                        if (optKey.isPresent()) {
                            WatchKey deletedFileKey = optKey.get();
                            LOG.debug("Cancelling events to {}", child);
                            deletedFileKey.cancel();
                            watchKeyDirMap.remove(deletedFileKey);
                        }
                        LOG.debug("Added deleted file {} to deletedFiles", foundFile.getCanonicalPath());
                    } else {
                        createdOrUpdatedFiles.add(foundFile);
                        LOG.debug("Added file {} to createdOrUpdatedFiles. kind: {}", foundFile.getCanonicalPath(), kind);
                    }
                }
                for (FileChangeListener fcl : fileChangeListeners) {
                    fcl.fireUpdateFiles(createdOrUpdatedFiles, deletedFiles);
                }
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (IOException | NotAllowedException e) {
            LOG.error("Exception in filewatcher loop. Exiting.", e);
        }
    }

    private Collection<File> getAllDirectories(Collection<File> dirs) throws IOException, NotAllowedException {
        Collection<File> allDirectories = new HashSet<>();
        dirs.forEach(dir -> allDirectories.addAll(listFilesAndDirs(dir, FileFilterUtils.falseFileFilter(), FileFilterUtils.directoryFileFilter())));
        LOG.debug("Returning {} directories", allDirectories.size());
        return allDirectories;
    }

}
