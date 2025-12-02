package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.util.GalleryFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deduplicates file events by receiving events and then withholds emitting them until the filesize has stabilized.
 */
public class Deduplicator {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    int intervalMillis = 500;
    int nrStableCyclesRequired = 3;
    Map<Path, FileSizeCounterPair> trackingMap = new ConcurrentHashMap<>();
    Thread thread;
    private volatile boolean running = true;
    List<Listener> listeners = new ArrayList<>();

    public Deduplicator() {
        LOG.debug("Initializing Deduplicator");
        Thread.ofVirtual().start(() -> {
            while (running) {
                try {
                    synchronized (this) {
                        wait(intervalMillis);
                        List<Path> stablePaths = getAndRemoveStablePaths();
                        if (stablePaths.isEmpty()) {
                            continue;
                        }
                        LOG.debug("Found {} stable paths. Will notify listeners. There are {} unstable paths waiting to stabilize",
                                stablePaths.size(), trackingMap.size());
                        listeners.forEach(listener -> {
                            try {
                                listener.onPathsChanged(stablePaths);
                            } catch (Exception e) {
                                LOG.error("Exception when notifying listener {}. Will proceed with other listeners", listener, e);
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    LOG.debug("Deduplicator thread interrupted");
                }
            }
            LOG.debug("Deduplicator stopped");
        });
    }

    public synchronized void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Adds a path. If it's a directory, listeners are notified immediately. It it's a file, it will be added to an internal structure,
     * which will emit the changed path once the size of the file is stable.
     *
     * @param path Path
     */
    public synchronized void add(Path path) {
        if (path.toFile().isDirectory()) {
            notifyListeners(List.of(path));
            return;
        }
        FileSizeCounterPair fileSizeCounterPair = trackingMap.get(path);
        if (fileSizeCounterPair == null) {
            trackingMap.put(path, new FileSizeCounterPair(path.toFile().length(), new AtomicInteger(0)));
        } else {
            if (fileSizeCounterPair.fileSize() != path.toFile().length()) {
                fileSizeCounterPair.counter().set(0);
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

    private synchronized void notifyListeners(List<Path> paths) {
        listeners.forEach(listener -> {
            try {
                listener.onPathsChanged(paths);
            } catch (Exception e) {
                LOG.error("Exception when notifying listener {}. Will proceed with other listeners", listener, e);
            }
        });
    }

    private synchronized List<Path> getAndRemoveStablePaths() {
        List<Path> stablePaths =
                trackingMap.entrySet().stream().filter(e -> e.getValue().counter().get() >= nrStableCyclesRequired).map(Map.Entry::getKey)
                        .sorted(GalleryFileUtils.shortestPathComparatorPath()).toList();
        stablePaths.forEach(trackingMap::remove);
        trackingMap.values().forEach(v -> v.counter().incrementAndGet());
        return stablePaths;
    }

    record FileSizeCounterPair(long fileSize, AtomicInteger counter) {
    }

    @FunctionalInterface
    public interface Listener {
        void onPathsChanged(List<Path> paths);
    }

}
