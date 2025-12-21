package com.github.henkexbg.gallery.job.listener;

import java.io.File;
import java.util.Set;

/**
 * Listener which is triggered when relevant files are created/updated/deleted.
 */
@FunctionalInterface
public interface FileChangeListener {

    /**
     * Triggered when a set of created/updated/deleted files have been detected within the allowed root paths of this application. Neither
     * parameter can be null, at least one of them will have at least one value, and one can be empty set
     *
     * @param upsertedFiles Created or updated files
     * @param deletedFiles  Deleted files
     */
    void onFilesUpdated(Set<File> upsertedFiles, Set<File> deletedFiles);

}
