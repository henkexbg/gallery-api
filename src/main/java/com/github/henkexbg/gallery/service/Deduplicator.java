package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.util.GalleryFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Deduplicates file events by receiving events and then withholds emitting them until the filesize has stabilized.
 */
public class Deduplicator {

    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private final Map<Path, FileInfo> trackingMap = new ConcurrentHashMap<>();
    private final Thread thread;
    private final List<Listener> listeners = new ArrayList<>();

    int intervalMillis = 500;
    int nrStableCyclesRequired = 3;
    int nrStableCyclesRequiredForDirectories = 1;
    private volatile boolean running = true;

    public Deduplicator() {
        LOG.debug("Initializing Deduplicator");
        thread = Thread.ofVirtual().start(() -> {
            while (running) {
                try {
                    List<Path> stablePaths;
                    List<Listener> privateListeners;
                    synchronized (this) {
                        wait(intervalMillis);
                        stablePaths = removeIncrementAndReturnStablePaths();
                        privateListeners = new ArrayList<>(listeners);
                        if (stablePaths.isEmpty()) {
                            continue;
                        }
                        LOG.debug("Found {} stable paths. Will notify listeners. There are {} unstable paths waiting to stabilize",
                                stablePaths.size(), trackingMap.size());

                        // Handle files in newly stabilized directories
                        List<Path> filesInStableDirs = new ArrayList<>();
                        stablePaths.stream().filter(p -> p.toFile().isDirectory()).forEach(dir -> {
                            try (Stream<Path> pathStream = Files.list(dir)) {
                                pathStream.filter(Files::isRegularFile).forEach(filesInStableDirs::add);
                            } catch (IOException e) {
                                LOG.error("Error while listing files in directory {}. Will proceed with other files", dir, e);
                            }
                        });
                        LOG.debug("Found {} files in newly stabilized directories", filesInStableDirs.size());
                        filesInStableDirs.forEach(this::add);
                    }
                    // Notify listeners with all stable paths outside of synchronized block
                    privateListeners.forEach(listener -> {
                        try {
                            LOG.debug("Notifying listener {} of stable paths {}", listener, stablePaths);
                            listener.onPathsChanged(stablePaths);
                        } catch (Exception e) {
                            LOG.error("Exception when notifying listener {}. Will proceed with other listeners", listener, e);
                        }
                    });
                } catch (InterruptedException e) {
                    LOG.debug("Deduplicator thread interrupted");
                }
            }
            LOG.debug("Deduplicator stopped");
        });
    }

    /**
     * Adds a listener that will be notified with paths that have stabilized.
     *
     * @param listener Listener
     * @see Listener
     */
    public synchronized void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Adds a path. Listeners will be notified once the underlying file is considered stable. For regular files this means the filesize is
     * stable over a certain period. Directories are kept for some time as well, and all regular files under that directory will be added
     * automatically just before the directory is emitted to listeners.
     *
     * @param path Path
     */
    public synchronized void add(Path path) {
        LOG.debug("Adding path {} to Deduplicator", path);
        boolean directory = path.toFile().isDirectory();
        FileInfo fileInfo = trackingMap.get(path);
        if (fileInfo == null) {
            trackingMap.put(path, new FileInfo(path.toFile().length(), new AtomicInteger(0), directory));
        } else if (!directory) {
            if (fileInfo.fileSize() != path.toFile().length()) {
                fileInfo.counter().set(0);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            notify();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                LOG.info("Interrupted while waiting for Deduplicator thread to finish");
            }
        }
    }

    /**
     * Removes all paths that have reached the stable state and returns them. Also increments the counter for all remaining paths.
     * @return A list of paths that have reached the stable state
     */
    private synchronized List<Path> removeIncrementAndReturnStablePaths() {
        List<Path> stablePaths = trackingMap.entrySet().stream()
                .filter(e -> (e.getValue().directory() && e.getValue().counter().get() >= nrStableCyclesRequiredForDirectories) ||
                        e.getValue().counter().get() >= nrStableCyclesRequired).map(Map.Entry::getKey)
                .sorted(GalleryFileUtils.shortestPathComparatorPath()).toList();
        stablePaths.forEach(trackingMap::remove);
        trackingMap.values().forEach(v -> v.counter().incrementAndGet());
        return stablePaths;
    }

    record FileInfo(long fileSize, AtomicInteger counter, boolean directory) {
    }

    /**
     * Listener that will be notified with paths that have stabilized.
     */
    @FunctionalInterface
    public interface Listener {
        void onPathsChanged(List<Path> paths);
    }

}
