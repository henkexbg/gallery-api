package com.github.henkexbg.gallery.util;

import org.apache.commons.lang3.Strings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class GalleryFileUtils {

    /**
     * Returns the canonical path of a file. Converts the typed exception to a RuntimeException to allow using from lambdas
     *
     * @param file File
     * @return The canonical path of the file
     */
    public static String getPathName(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ioe) {
            throw new RuntimeException("IOException when checking file path name", ioe);
        }
    }

    /**
     * Comparator of files that sorts by shortest path name first to try and ensure that directories are handled before files within that
     * directory.
     *
     * @return A comparator
     */
    public static Comparator<File> shortestPathComparatorFile() {
        return Comparator.comparingInt(f -> getPathName(f).length());
    }

    /**
     * Same as {@link #shortestPathComparatorFile()} but for Paths.
     *
     * @return A comparator
     */
    public static Comparator<Path> shortestPathComparatorPath() {
        return Comparator.comparingInt(f -> getPathName(f.toFile()).length());
    }

    /**
     * Small util method helping with escaping any characters that would not be allowed in a path. The obvious use case here is Windows, and
     * it's drive letter followed by a ':'. Since the whole path will be appended to another root path that character is not allowed.
     *
     * @param file File
     * @return The escaped path
     * @throws IOException If the path cannot be determined
     */
    public static String escapeFilePath(File file) throws IOException {
        return file.getCanonicalPath().replace(":", "_");
    }

    /**
     * Simple helper method that determines whether a file is a video.
     *
     * @param file File
     * @return True if video
     * @throws IllegalArgumentException If content type cannot be established
     */
    public static boolean isVideo(File file) throws IllegalArgumentException {
        try {
            return Strings.CS.startsWith(getContentType(file), "video");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not determine whether %s is a video".formatted(file));
        }
    }

    /**
     * Determines the content type for a given file. Will delegate to JVM/operating system.
     *
     * @param file File.
     * @return Content type for given file.
     * @throws IOException If content type cannot be established
     */
    public static String getContentType(File file) throws IOException {
        return Files.probeContentType(file.toPath());
    }
}
