/**
 * Copyright (c) 2016 Henrik Bjerne
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:The above copyright
 * notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.henkexbg.gallery.service;

import static org.apache.commons.io.FilenameUtils.*;
import static org.apache.commons.io.FileUtils.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.github.henkexbg.gallery.bean.GalleryDirectory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.bean.GalleryFile.GalleryFileType;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The core service providing the business logic for the application. The basic
 * structure is that this service maps between "public" paths and actual files
 * on the file system. Different users can have different public paths actually
 * pointing to the same actual file. The public path always starts with a root
 * dir as returned by {@link #getRootDirectories()}. Each root dir is publicly
 * just a name, whereas the implementation of this class will map that to an actual file
 * <p>
 * Each root dir can then contain additional directories, as well as files. In
 * the interest of this service, there are images and videos.
 *
 * @author Henrik Bjerne
 */
@Service
public class GalleryService {

    public static final String VIDEO_MODE_ORIGINAL = "ORIGINAL";

    public static final String DEFAULT_IMAGE_FILE_ENDING = "jpg";

    public static final String DIR_IMAGE_DIR_NAME = "_directoryImages_";

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    private GalleryAuthorizationService galleryAuthorizationService;

    @Resource
    private ImageResizeService imageResizeService;

    @Resource
    private VideoConversionService videoConversionService;

    @Value("${gallery.resizeDir}")
    private File resizeDir;

    @Value("#{'${gallery.allowedFileExtensions}'.split(',')}")
    private Set<String> allowedFileExtensions;

    private final IOFileFilter fileFilter = new CaseInsensitiveFileEndingFilter();

    @Value("${gallery.imageResizing.maxImageWidth}")
    private int maxImageWidth = 5000;

    @Value("${gallery.imageResizing.maxImageHeight}")
    private int maxImageHeight = 5000;

    @Value("${gallery.imageResizing.directoryImageMaxAgeMinutes}")
    private int directoryImageMaxAgeMinutes = 1440;

    private File dirImageDir;

    @PostConstruct
    public void setUp() {
        dirImageDir = new File(resizeDir, DIR_IMAGE_DIR_NAME);
        if (!dirImageDir.exists()) {
            dirImageDir.mkdir();
        }
    }

    /**
     * Retrieves the root directories for the current user.
     *
     * @return A list of root dir names.
     * @throws IOException
     *             If any issues retrieving the files.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    public List<GalleryDirectory> getRootDirectories() throws IOException, NotAllowedException {
        List<String> rootDirCodes = new ArrayList<String>(galleryAuthorizationService.getRootPathsForCurrentUser().keySet());
        // Unfortunately cannot use parallelstream as the security context gets lost in any child threads
        List<GalleryDirectory> galleryDirectories = rootDirCodes.stream().map(r -> {
            try {
                File oneRootDir = getRealFileOrDir(r);
                return createGalleryDirectory(r, oneRootDir, r);
            } catch (IOException | NotAllowedException e) {
                LOG.error("Error when generating root dir {}. Will skip. Exception: {}", r, e);
                return null;
            }
        }).filter(gd -> gd != null).sorted((gd1, gd2) -> gd1.getName().compareTo(gd2.getName())).collect(Collectors.toList());
        return galleryDirectories;
    }

    /**
     * Retrieves an image with the specified dimensions. The implementation
     * should make sure the image is properly rescaled. The service may also
     * define max and min image sizes. If a directory is provided, a directory image should be provided, given that
     * there are images in the sub-directories. If not, this may return a {@link java.io.FileNotFoundException}
     *
     * @param publicPath
     *            Public path.
     * @param width
     *            Width in pixels
     * @param height
     *            Height in pixels.
     * @return The rescaled image.
     * @throws IOException
     *             If any issues retrieving the files, or the given format is
     *             not valid.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
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
            realFile = getDirectoryImage(realFile);
            if (realFile == null) {
                // Should never happen, but directory images can exist as empty files if there
                // are no images in that
                // directory. This is just an extra check that we don't even bother to resize
                // that case
                throw new FileNotFoundException();
            }
        }
        File resizedImage = null;
        boolean isVideo = isVideo(realFile);
        if (isVideo) {
            resizedImage = determineResizedVideoImage(realFile, width, height);
        } else {
            resizedImage = determineResizedImage(realFile, width, height);
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
     * Retrieves all the files (not subdirectories) from the given public path.
     *
     * @param publicPath
     *            Public path.
     * @return A list of <code>GalleryFile</code> objects.
     * @throws IOException
     *             If any issues retrieving the files.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    public List<GalleryFile> getDirectoryListingFiles(String publicPath) throws IOException, NotAllowedException {
        File dir = getRealFileOrDir(publicPath);
        if (!dir.isDirectory()) {
            LOG.debug("File {} is not a directory. Returning null.", dir);
            return null;
        }
        Collection<File> fileCollection = (Collection<File>) listFiles(dir, fileFilter, null);
        List<File> fileList = new ArrayList<File>(fileCollection);
        Collections.sort(fileList, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
        LOG.debug("Found {} files for path {}", fileList.size(), publicPath);
        ArrayList<GalleryFile> galleryFiles = new ArrayList<GalleryFile>(fileList.size());

        for (File oneFile : fileList) {
            galleryFiles.add(createGalleryFile(publicPath + '/' + oneFile.getName(), oneFile));
        }
        return galleryFiles;
    }

    /**
     * Retrieves all sub-directories of the current public path. Only
     * directories are returned by this method.
     *
     * @param publicPath
     *            Public path.
     * @return A list of public paths pointing to directories, or empty list.
     * @throws IOException
     *             If any issues retrieving the files.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    public List<GalleryDirectory> getDirectories(String publicPath) throws IOException, NotAllowedException {
        File dir = getRealFileOrDir(publicPath);
        if (dir.isDirectory()) {
            List<File> directories = Arrays.asList(dir.listFiles(File::isDirectory));
            LOG.debug("Found {} directories for path {}", directories.size(), publicPath);
            // Unfortunately cannot use parallelstream as the security context gets lost in any child threads
            List<GalleryDirectory> galleryDirectories = directories.stream().map(d -> {
                String oneDirPublicPath = buildPublicPathForFileInPublicDir(publicPath, d);
                return createGalleryDirectory(oneDirPublicPath, d);
            }).sorted((gd1, gd2) -> gd1.getName().compareTo(gd2.getName())).collect(Collectors.toList());
            return galleryDirectories;
        }
        return Collections.emptyList();
    }

    /**
     * Retrieve all videos from all allowed root dirs. The original intention
     * with this is mostly for back end purposes (such as batch conversion).
     *
     * @return A list of all videos.
     * @throws IOException
     *             If any issues retrieving the files.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    public List<GalleryFile> getAllVideos() throws IOException, NotAllowedException {
        Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
        Map<String, Collection<File>> rootPathVideos = new HashMap<>();
        for (Entry<String, File> oneEntry : rootPathsForCurrentUser.entrySet()) {
            List<File> videosForRootPath = listFiles(oneEntry.getValue(), fileFilter, FileFilterUtils.directoryFileFilter()).stream().filter(f -> isVideoNoException(f)).collect(Collectors.toList());
            rootPathVideos.put(oneEntry.getKey(), videosForRootPath);
        }
        List<GalleryFile> galleryFiles = new ArrayList<>();
        for (Entry<String, Collection<File>> oneEntry : rootPathVideos.entrySet()) {
            for (File oneFile : oneEntry.getValue()) {
                galleryFiles.add(getGalleryFile(getPublicPathFromRealFile(oneEntry.getKey(), oneFile)));
            }
        }
        LOG.debug("Returning {} video files", galleryFiles.size());
        return galleryFiles;
    }

    /**
     * Generic method retrieving a gallery file.
     *
     * @param publicPath
     *            Public path.
     * @return <code>GalleryFile</code>
     * @throws IOException
     *             If any issues retrieving the files.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    public GalleryFile getGalleryFile(String publicPath) throws IOException, NotAllowedException {
        LOG.debug("Entering getGalleryFile(publicPath={})", publicPath);
        File file = getRealFileOrDir(publicPath);
        if (file.isDirectory()) {
            throw new FileNotFoundException("Directories not allowed in this method!");
        }
        return createGalleryFile(publicPath, file);
    }

    /**
     * Returns a list of different video modes that are allowed to use. These
     * modes could for instance be different quality settings.
     *
     * @return A list of video modes.
     */
    public List<String> getAvailableVideoModes() {
        List<String> availableVideoModes = new ArrayList<>(videoConversionService.getAvailableVideoModes());
        availableVideoModes.add(VIDEO_MODE_ORIGINAL);
        return availableVideoModes;
    }

    /**
     * Retrieves a video for a given video mode.
     *
     * @param publicPath
     *            Public path
     * @param videoMode
     *            Video mode. Has to be one that is returned by
     *            {@link #getAvailableVideoModes()}.
     * @return A gallery file with the video for the given video mode.
     * @throws IOException
     *             If any issues retrieving the files, or if video mode
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    public GalleryFile getVideo(String publicPath, String videoMode) throws IOException, NotAllowedException {
        LOG.debug("Entering getVideo(publicPath={}, videoMode={}", publicPath, videoMode);
        if (StringUtils.isEmpty(videoMode) || !getAvailableVideoModes().contains(videoMode)) {
            throw new IOException("videoMode not defined!");
        }
        File video = getRealFileOrDir(publicPath);
        File convertedVideo = null;
        if (VIDEO_MODE_ORIGINAL.equals(videoMode)) {
            LOG.debug("Video mode was {}. Will return original video.", VIDEO_MODE_ORIGINAL);
            convertedVideo = video;
        } else {
            convertedVideo = determineConvertedVideo(video, videoMode);
            LOG.debug("Converted video filename: {}", convertedVideo);
            if (!convertedVideo.exists()) {
                LOG.debug("Resized file did not exist.");
                if (!video.exists()) {
                    String errorMessage = String.format("Main video %s did not exist. Could not resize.", video.getCanonicalPath());
                    LOG.error(errorMessage);
                    throw new FileNotFoundException(errorMessage);
                }
                videoConversionService.convertVideo(video, convertedVideo, videoMode);
            }
        }
        return createGalleryFile(publicPath, convertedVideo);
    }

    /**
     * Generates a public path for a file, given the public path of the directory
     * the file resides in and the actual file.
     *
     * @param directoryPublicPath Public path of directory.
     * @param fileInPublicDir     Actual file.
     * @return The public path for the given file
     */
    private String buildPublicPathForFileInPublicDir(String directoryPublicPath, File fileInPublicDir) {
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(directoryPublicPath);
        pathBuilder.append(File.separator);
        pathBuilder.append(fileInPublicDir.getName());
        return separatorsToUnix(pathBuilder.toString());
    }

    /**
     * Looks up the actual file based on the public path. This method also checks
     * that the current user has right to access the file in question.
     *
     * @param publicPath Public path
     * @return The corresponding file, if existing and user is allowed to access
     * @throws IOException           If any file operation fails
     * @throws FileNotFoundException If file cannot be found
     * @throws NotAllowedException   If explicitly not allowed to access file
     */
    public File getRealFileOrDir(String publicPath) throws IOException, FileNotFoundException, NotAllowedException {
        LOG.debug("Entering getRealFileOrDir(publicPath={})", publicPath);
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
     * @throws IOException
     * @throws NotAllowedException
     */
    private String getPublicPathFromRealFile(String publicRoot, File file) throws IOException, NotAllowedException {
        String actualFilePath = file.getCanonicalPath();
        File rootFile = galleryAuthorizationService.getRootPathsForCurrentUser().get(publicRoot);
        String relativePath = actualFilePath.substring(rootFile.getCanonicalPath().length());
        String publicPath = separatorsToUnix(publicRoot + relativePath);
        LOG.debug("Actual file: {}, generated public path: {}", file, publicPath);
        return publicPath;
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
     * Creates a {@link GalleryFile} for the given file. Public path is required as
     * multiple public paths can point to the same actual file. This method should
     * always be given a file with an approved image or video, and never a
     * directory.
     *
     * @param publicPath Public path.
     * @param actualFile File to convert to {@link GalleryFile}.
     * @return A {@link GalleryFile} based on the given parameters
     * @throws IOException
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
     * Checks whether file has an allowed file extension. Checked in a case insensitive way.
     *
     * @param file File
     * @return True if allowed
     */
    public boolean isAllowedExtension(File file) {
        return allowedFileExtensions.contains(getExtension(file.getName()).toLowerCase());
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
        return createGalleryDirectory(publicPath, actualDir, actualDir.getName());
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
    private GalleryDirectory createGalleryDirectory(String publicPath, File actualDir, String dirName) {
        GalleryDirectory galleryDirectory = new GalleryDirectory();
        galleryDirectory.setPublicPath(publicPath);
        galleryDirectory.setName(dirName);
        try {
            File directoryImage = getDirectoryImage(actualDir);
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
     * Retrieves the image for a directory. If necessary the image will be generated
     * first.
     *
     * @param directory Directory
     * @return The generated image, or null if no image could be generated, for
     * example because there are no images in the directory.
     * @throws IOException
     */
    private File getDirectoryImage(File directory) throws IOException {
        File directoryImage = determineDirectoryImage(directory);
        if (!directoryImage.exists() || directoryImage.lastModified() < System.currentTimeMillis() - (directoryImageMaxAgeMinutes * 60000)) {
            LOG.debug("Evaluating directory image for {}", directory);
            List<File> imagesForCompositeDirectoryImage = findImagesForCompositeDirectoryImage(directory);
            if (!imagesForCompositeDirectoryImage.isEmpty()) {
                long newestSourceImageTimestamp = imagesForCompositeDirectoryImage.stream().sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified())).findFirst().get().lastModified();
                if (directoryImage.exists() && newestSourceImageTimestamp < directoryImage.lastModified()) {
                    // Extra optimization. If the directory image has expired, but the composite
                    // images are not newer than the current directory image, just update the last
                    // modified timestamp on the directory image
                    LOG.debug("Keeping expired directory image, renewing timestamp");
                    directoryImage.setLastModified(System.currentTimeMillis());
                } else {
                    LOG.debug("Will generate new composite image for directory {}", directoryImage);
                    try {
                        imageResizeService.generateCompositeImage(imagesForCompositeDirectoryImage, directoryImage, maxImageWidth, maxImageHeight);
                    } catch (IOException ioe) {
                        String errorMessage = String.format("Error when generating composite image for %s. Returning null.", directory.getCanonicalPath());
                        LOG.error(errorMessage, ioe);
                        directoryImage = null;
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
    private List<File> findImagesForCompositeDirectoryImage(File directory) {
        final int nrImages = 4;
        List<File> foundFiles = listFiles(directory, fileFilter, null).stream().filter(f -> !isVideoNoException(f)).collect(Collectors.toList());
        if (foundFiles.size() >= nrImages) {
            return foundFiles;
        }
        File[] directories = directory.listFiles(File::isDirectory);
        if (directories != null) {
            int i = 0;
            while (i < directories.length && foundFiles.size() < nrImages) {
                foundFiles.addAll(listFiles(directories[i], fileFilter, null).stream().filter(f -> !isVideoNoException(f)).toList());
                i++;
            }
        }
        return foundFiles.subList(0, Math.min(nrImages, foundFiles.size()));
    }

    /**
     * Determines the content type for a given file. Will delegate to JVM/operating
     * system.
     *
     * @param file File.
     * @return Content type for given file.
     * @throws IOException
     */
    private String getContentType(File file) throws IOException {
        return Files.probeContentType(file.toPath());
    }

    /**
     * Simpler helper method that ignores the possible exception when checking
     * whether a given file is a video. Will return false if an exception is caught.
     *
     * @param file File.
     * @return True if video, false if not or exception was thrown.
     */
    private boolean isVideoNoException(File file) {
        try {
            return isVideo(file);
        } catch (IOException ioe) {
            return false;
        }
    }

    /**
     * Simpler helper method that determines whether a file is a video.
     *
     * @param file File.
     * @return True if video.
     * @throws IOException
     */
    private boolean isVideo(File file) throws IOException {
        return StringUtils.startsWith(getContentType(file), "video");
    }

    /**
     * Generates the filename for a resized file and creates a file object (does not
     * perform any file operation) given a file and its rescaling parameters. resize
     * parameters.
     *
     * @param originalFile
     * @param width
     * @param height
     * @return
     * @throws IOException
     */
    private File determineResizedImage(File originalFile, int width, int height) throws IOException {
        String resizePart = Integer.valueOf(width).toString() + "x" + Integer.valueOf(height).toString();
        File resizedImage = new File(resizeDir, File.separator + resizePart + File.separator + escapeFilePath(originalFile) + (originalFile.isDirectory() ? '.' + DEFAULT_IMAGE_FILE_ENDING : ""));
        return resizedImage;
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
     * @throws IOException
     */
    private File determineResizedVideoImage(File originalFile, int width, int height) throws IOException {
        File resizedImage = determineResizedImage(originalFile, width, height);
        return new File(resizedImage.getCanonicalPath() + '.' + DEFAULT_IMAGE_FILE_ENDING);
    }

    /**
     * Generates the filename for a converted video and creates a file object (does
     * not perform any file operation) given an original video file and its
     * rescaling parameters.
     *
     * @param originalFile Video.
     * @param videoMode    Video mode as per {@link #getAvailableVideoModes()}.
     * @return A {@link File} object for the converted video
     * @throws IOException
     */
    private File determineConvertedVideo(File originalFile, String videoMode) throws IOException {
        File convertedVideo = new File(resizeDir, File.separator + videoMode + File.separator + escapeFilePath(originalFile));
        return convertedVideo;
    }

    /**
     * Determines the file (or essentially filename) of an image dedicated for a
     * directory.
     *
     * @param directory Directory
     * @return A file pointing to the directory image.
     * @throws IOException
     */
    private File determineDirectoryImage(File directory) throws IOException {
        String filename = directory.getName() + '-' + directory.getCanonicalPath().hashCode() + '.' + DEFAULT_IMAGE_FILE_ENDING;
        File dirImage = new File(dirImageDir, filename);
        return dirImage;
    }

    /**
     * Small util method helping with escaping any characters that would not be
     * allowed in a path. The obvious use case here is Windows and it's drive letter
     * followed by a ':'. Since the whole path will be appended to another root path
     * that character is not allowed.
     *
     * @param file
     * @return
     * @throws IOException
     */
    private String escapeFilePath(File file) throws IOException {
        return file.getCanonicalPath().replace(":", "_");
    }

    /**
     * Filters allowed files based on file ending. Special case as well for
     * directory images that are generated by this class, that should not be part of
     * normal file listing.
     */
    private class CaseInsensitiveFileEndingFilter implements IOFileFilter {

        @Override
        public boolean accept(File file) {
            return isAllowedExtension(file);
        }

        @Override
        public boolean accept(File dir, String name) {
            return false;
        }

    }

}