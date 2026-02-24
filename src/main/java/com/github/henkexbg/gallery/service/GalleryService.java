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

import static com.github.henkexbg.gallery.util.GalleryFileUtils.*;
import static org.apache.commons.io.FilenameUtils.*;
import static org.apache.commons.io.FileUtils.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.github.henkexbg.gallery.bean.GalleryDirectory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.bean.GalleryFile.GalleryFileType;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Provides functionality to interact directly with GalleryFiles and their underlying actual files. Different users can have different
 * public paths actually pointing to the same actual file. The public path always starts with a root directory as returned by
 * {@link GalleryAuthorizationService#getRootPathsForCurrentUser()}. Each root dir is publicly just a name, whereas the implementation of
 * this class will map that to an actual directory.
 *
 * @author Henrik Bjerne
 */
@Service
public class GalleryService {

    public static final String VIDEO_MODE_ORIGINAL = "ORIGINAL";
    public static final String DEFAULT_IMAGE_FILE_ENDING = "jpg";
    public static final String DIR_IMAGE_DIR_NAME = "_directoryImages_";

    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private final IOFileFilter allowedMediaExtensionsFilter = new AllowedMediaExtensionsFilter();

    @Resource
    GalleryAuthorizationService galleryAuthorizationService;

    @Resource
    ImageResizeService imageResizeService;

    @Resource
    VideoConversionService videoConversionService;

    @Value("${gallery.resizeDir}")
    File resizeDir;

    @Value("#{'${gallery.allowedFileExtensions}'.split(',')}")
    Set<String> allowedFileExtensions;

    @Value("${gallery.imageResizing.maxImageWidth}")
    int maxImageWidth = 5000;

    @Value("${gallery.imageResizing.maxImageHeight}")
    int maxImageHeight = 5000;

    @Value("${gallery.imageResizing.directoryImageMaxAgeMinutes}")
    long directoryImageMaxAgeMinutes = 1440;

    File dirImageDir;

    @PostConstruct
    public void setUp() {
        dirImageDir = new File(resizeDir, DIR_IMAGE_DIR_NAME);
        if (!dirImageDir.exists()) {
            boolean created = dirImageDir.mkdir();
            if (!created) {
                throw new RuntimeException("Could not create directory: " + dirImageDir.getAbsolutePath());
            }
        }
    }

    /**
     * Retrieves an image with the specified dimensions. Videos and directories can also be given to this method, in which case image
     * representation of those are returned.
     *
     * @param publicPath Public path
     * @param width Width in pixels
     * @param height Height in pixels
     * @return The rescaled image
     * @throws IOException If any issues retrieving the files, or the given format is not valid
     * @throws NotAllowedException If the requested path is not allowed
     */
    public GalleryFile getImage(String publicPath, int width, int height) throws IOException, NotAllowedException {
        LOG.debug("Entering getImage(publicPath={}, width={}, height={}", publicPath, width, height);
        if (width <= 0 || width > maxImageWidth || height <= 0 || height > maxImageHeight) {
            String errorMessage = String.format("Non valid image size requested. Width: %s, height: %s", width, height);
            LOG.error(errorMessage);
            throw new IOException(errorMessage);
        }
        File realFile = getRealFileOrDir(publicPath);
        if (realFile.isDirectory()) {
            realFile = getDirectoryImage(realFile, true);
            if (realFile == null) {
                // Should never happen, but directory images can exist as empty files if there are no images in that directory. This is just
                // an extra check that we don't even bother to resize that case
                throw new FileNotFoundException();
            }
        }
        File resizedImage;
        boolean isVideo = isVideo(realFile);
        if (isVideo) {
            resizedImage = determineResizedVideoImage(realFile, width, height);
        } else {
            resizedImage = determineResizedImageFilename(realFile, width, height);
        }
        LOG.debug("Resized filename: {}", resizedImage.getCanonicalPath());
        if (!resizedImage.exists()) {
            LOG.debug("Resized file did not exist.");
            if (!realFile.exists()) {
                String errorMessage = String.format("Main realFile %s did not exist. Could not resize.", realFile.getCanonicalPath());
                LOG.error(errorMessage);
                throw new FileNotFoundException(errorMessage);
            }
            if (isVideo) {
                videoConversionService.generateImageForVideo(realFile, resizedImage, width, height);
            } else {
                imageResizeService.resizeImage(realFile, resizedImage, width, height);
            }
        }
        return createGalleryFile(publicPath, resizedImage);
    }

    /**
     * Retrieves a video for a given video mode.
     *
     * @param publicPath Public path
     * @param videoMode Video mode
     * @return A gallery file with the video for the given video mode.
     * @throws IOException If any issues retrieving the files, or if video mode
     * @throws NotAllowedException If the requested path is not allowed.
     */
    public GalleryFile getVideo(String publicPath, String videoMode) throws IOException, NotAllowedException {
        LOG.debug("Entering getVideo(publicPath={}, videoMode={}", publicPath, videoMode);
        File video = getRealFileOrDir(publicPath);
        File convertedVideo;
        if (VIDEO_MODE_ORIGINAL.equals(videoMode)) {
            LOG.debug("Video mode was {}. Will return original video.", VIDEO_MODE_ORIGINAL);
            convertedVideo = video;
        } else {
            convertedVideo = videoConversionService.getConvertedVideo(video, videoMode);
        }
        return createGalleryFile(publicPath, convertedVideo);
    }

    public String getPublicPathFromRealFile(File file) throws IOException, NotAllowedException {
        Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
        Optional<Entry<String, File>> optRootEntry = rootPathsForCurrentUser.entrySet().stream().filter(e -> {
            try {
                return file.getCanonicalPath().startsWith(e.getValue().getCanonicalPath());
            } catch (IOException ioe) {
                return false;
            }
        }).findAny();
        if (optRootEntry.isEmpty()) {
            throw new IOException(String.format("File %s could not be mapped to a public path", file.getCanonicalPath()));
        }
        Entry<String, File> rootEntry = optRootEntry.get();
        return getPublicPathFromRealFile(rootEntry.getKey(), file);
    }

    /**
     * Checks whether file has an allowed file extension. Checked in a case insensitive way.
     *
     * @param file File
     * @return True if allowed
     */
    public boolean isAllowedExtension(File file) {
        return allowedFileExtensions.contains(getExtension(file.getName()).toLowerCase());
    }

    /**
     * Creates a {@link GalleryFile} for the given file. Public path is required as
     * multiple public paths can point to the same actual file. This method should
     * always be given a file with an approved image or video, and never a
     * directory.
     *
     * @param publicPath Public path.
     * @param actualFile File to convert to {@link GalleryFile}.
     * @return A {@link GalleryFile} based on the given parameters
     * @throws IOException           If any file operation fails
     */
    public GalleryFile createGalleryFile(String publicPath, File actualFile) throws IOException {
        String contentType = getContentType(actualFile);
        GalleryFile galleryFile = new GalleryFile();
        galleryFile.setPublicPath(publicPath);
        galleryFile.setActualFile(actualFile);
        galleryFile.setContentType(contentType);
        if (isVideo(actualFile)) {
            galleryFile.setType(GalleryFileType.VIDEO);
        } else {
            galleryFile.setType(GalleryFileType.IMAGE);
        }
        return galleryFile;
    }

    /**
     * As {@link #createGalleryDirectory(String, File, String)}, but uses the
     * directory name as gallery directory name.
     *
     * @param publicPath Public path.
     * @param actualDir  Directory.
     * @return A {@link GalleryDirectory} for the given parameters
     */
    public GalleryDirectory createGalleryDirectory(String publicPath, File actualDir) {
        String dirName = publicPath.contains("/") ? actualDir.getName() : publicPath;
        return createGalleryDirectory(publicPath, actualDir, dirName);
    }

    /**
     * Looks up the actual file based on the public path. This method also checks that the current user has right to access the file.
     *
     * @param publicPath Public path
     * @return The corresponding file, if existing and user is allowed to access
     * @throws IOException           If any file operation fails
     * @throws FileNotFoundException If file cannot be found
     * @throws NotAllowedException   If explicitly not allowed to access file
     */
    File getRealFileOrDir(String publicPath) throws IOException, FileNotFoundException, NotAllowedException {
        File file = galleryAuthorizationService.getRealFileOrDir(publicPath);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found!");
        }
        if (!file.isDirectory() && !isAllowedExtension(file)) {
            throw new NotAllowedException("File " + publicPath + " did not have an allowed file extension");
        }
        return file;
    }

    /**
     * A kind of inverse lookup - finding the public path given the actual file.
     * <strong>NOTE! This method does NOT verify that the current user actually has
     * the right to access the given publicRoot! It is the responsibility of calling
     * methods to make sure only allowed root paths are used.</strong>
     *
     * @param publicRoot Public root dir
     * @param file       Actual file
     * @return The public path of the given file for the given publicRoot.
     * @throws IOException           If any file operation fails
     */
    String getPublicPathFromRealFile(String publicRoot, File file) throws IOException {
        String actualFilePath = file.getCanonicalPath();
        File rootFile = galleryAuthorizationService.getRootPathsForCurrentUser().get(publicRoot);
        String relativePath = actualFilePath.substring(rootFile.getCanonicalPath().length());
        return  separatorsToUnix(publicRoot + relativePath);
    }

    /**
     * Creates a {@link GalleryDirectory} given the public path and the actual
     * directory. Public path is required as multiple public paths can point to the
     * same actual directory.
     *
     * @param publicPath Public path.
     * @param actualDir  Directory.
     * @param dirName    Name of gallery directory to be created
     * @return A {@link GalleryDirectory} for the given parameters
     */
    GalleryDirectory createGalleryDirectory(String publicPath, File actualDir, String dirName) {
        GalleryDirectory galleryDirectory = new GalleryDirectory();
        galleryDirectory.setPublicPath(publicPath);
        galleryDirectory.setName(dirName);
        try {
            File directoryImage = getDirectoryImage(actualDir, false);
            if (directoryImage != null) {
                // Use the public path of the directory, and combine it with the image
                galleryDirectory.setImage(createGalleryFile(publicPath, directoryImage));
            }
        } catch (IOException ioe) {
            LOG.error("Could not generate directory image for {}", actualDir);
        }
        return galleryDirectory;
    }

    /**
     * Retrieves the image for a directory. If necessary the image will be generated first.
     *
     * @param directory       Directory
     * @param createIfMissing If true, the directory image will be generated if missing
     * @return The generated image, or null if no image could be generated, for example because there are no images in the directory.
     * @throws IOException If any file operation fails
     */
    File getDirectoryImage(File directory, boolean createIfMissing) throws IOException {
        File directoryImage = determineDirectoryImage(directory);
        if (createIfMissing && (!directoryImage.exists() ||
                directoryImage.lastModified() < System.currentTimeMillis() - (directoryImageMaxAgeMinutes * 60000))) {
            LOG.debug("Evaluating directory image for {}", directory);
            List<File> imagesForCompositeDirectoryImage = findImagesForCompositeDirectoryImage(directory);
            if (!imagesForCompositeDirectoryImage.isEmpty()) {
                long newestSourceImageTimestamp =
                        imagesForCompositeDirectoryImage.stream().min((a, b) -> Long.compare(b.lastModified(), a.lastModified())).get()
                                .lastModified();
                if (directoryImage.exists() && newestSourceImageTimestamp < directoryImage.lastModified()) {
                    // Extra optimization. If the directory image has expired, but the composite images are not newer than the current
                    // directory image, just update the last modified timestamp on the directory image
                    LOG.debug("Keeping expired directory image, renewing timestamp");
                    directoryImage.setLastModified(System.currentTimeMillis());
                } else {
                    LOG.debug("Will generate new composite image for directory {}", directoryImage);
                    try {
                        imageResizeService.generateCompositeImage(imagesForCompositeDirectoryImage, directoryImage, maxImageWidth,
                                maxImageHeight);
                    } catch (IOException ioe) {
                        String errorMessage = String.format("Error when generating composite image for %s. Returning null.",
                                directory.getCanonicalPath());
                        LOG.error(errorMessage, ioe);
                        directoryImage.createNewFile();
                    }
                }
            } else {
                // Create empty file so that we can check the timestamp towards it and not
                // always try to generate a new file for directories without images
                directoryImage.createNewFile();
            }
        }
        if (directoryImage == null || !directoryImage.exists() || directoryImage.length() == 0) {
            return null;
        }
        return directoryImage;
    }

    /**
     * Searches through the given directory for images that can be used for a
     * composite directory image.
     *
     * @param directory Directory
     * @return A list with files pointing to images of approved file content types.
     * May return empty list if none found
     */
    List<File> findImagesForCompositeDirectoryImage(File directory) {
        final int nrImages = 4;
        List<File> foundFiles =
                listFiles(directory, allowedMediaExtensionsFilter, null).stream().filter(f -> !isVideo(f)).collect(Collectors.toList());
        if (foundFiles.size() >= nrImages) {
            return foundFiles;
        }
        File[] directories = directory.listFiles(File::isDirectory);
        if (directories != null) {
            int i = 0;
            while (i < directories.length && foundFiles.size() < nrImages) {
                foundFiles.addAll(listFiles(directories[i], allowedMediaExtensionsFilter, null).stream().filter(f -> !isVideo(f)).toList());
                i++;
            }
        }
        return foundFiles.subList(0, Math.min(nrImages, foundFiles.size()));
    }

    /**
     * Generates the filename for a resized file and creates a file object (does not
     * perform any file operation) given a file and its rescaling parameters. resize
     * parameters.
     *
     * @param originalFile Progoma; fo;e
     * @param width Width
     * @param height Height
     * @return A file with the generated filename
     * @throws IOException If filename cannot be generated
     */
    File determineResizedImageFilename(File originalFile, int width, int height) throws IOException {
        String resizePart = Integer.valueOf(width).toString() + "x" + Integer.valueOf(height).toString();
        return new File(resizeDir, File.separator + resizePart + File.separator + escapeFilePath(originalFile) +
                (originalFile.isDirectory() ? '.' + DEFAULT_IMAGE_FILE_ENDING : ""));
    }

    /**
     * Generates the filename for a resized image for a video and creates a file
     * object (does not perform any file operation) given a video file and its
     * rescaling parameters. resize parameters.
     *
     * @param originalFile Video.
     * @param width        Max width to scale image to.
     * @param height       Max height to scale image to.
     * @return A {@link File} object for the scaled image.
     * @throws IOException If filename cannot be determined
     */
    File determineResizedVideoImage(File originalFile, int width, int height) throws IOException {
        File resizedImage = determineResizedImageFilename(originalFile, width, height);
        return new File(resizedImage.getCanonicalPath() + '.' + DEFAULT_IMAGE_FILE_ENDING);
    }

    /**
     * Determines the file (or essentially filename) of an image dedicated for a
     * directory.
     *
     * @param directory Directory
     * @return A file pointing to the directory image.
     * @throws IOException If filename cannot be determined
     */
    File determineDirectoryImage(File directory) throws IOException {
        String filename = directory.getName() + '-' + directory.getCanonicalPath().hashCode() + '.' + DEFAULT_IMAGE_FILE_ENDING;
        return new File(dirImageDir, filename);
    }

    /**
     * Filter allows all media extensions files. The files must also exist and have a length > 0
     */
    class AllowedMediaExtensionsFilter implements IOFileFilter {

        @Override
        public boolean accept(File file) {
            return file.exists() && file.length() > 0 && isAllowedExtension(file);
        }

        @Override
        public boolean accept(File dir, String name) {
            return false;
        }

    }

}