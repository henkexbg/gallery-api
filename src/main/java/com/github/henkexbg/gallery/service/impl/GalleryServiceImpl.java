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
package com.github.henkexbg.gallery.service.impl;

import static org.apache.commons.io.FilenameUtils.*;
import static org.apache.commons.io.FileUtils.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.service.ImageResizeService;
import com.github.henkexbg.gallery.service.VideoConversionService;
import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.bean.GalleryFile.GalleryFileType;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

/**
 * Implementation of the {@link GalleryService} interface. A number of services are
 * tied together via this class, such as services for authorization, resizing
 * and conversion. In addition, this class defines a number of file formats it
 * will accept. Any other formats will be disregarded even if the files are
 * allowed in terms of location.
 *
 * @author Henrik Bjerne
 */
public class GalleryServiceImpl implements GalleryService {

    public static final String VIDEO_MODE_ORIGINAL = "ORIGINAL";

    public static final String VIDEO_IMAGE_FILE_ENDING = "jpg";

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private GalleryAuthorizationService galleryAuthorizationService;

    private ImageResizeService imageResizeService;

    private VideoConversionService videoConversionService;

    private File resizeDir;

    private Set<String> allowedFileExtensions;

    private final Comparator<String> directoryNameComparator = new CaseInsensitiveComparator();

    private final IOFileFilter fileFilter = new CaseInsensitiveFileEndingFilter();

    private int maxImageWidth = 5000;

    private int maxImageHeight = 5000;

    @Required
    public void setGalleryAuthorizationService(GalleryAuthorizationService galleryAuthorizationService) {
        this.galleryAuthorizationService = galleryAuthorizationService;
    }

    @Required
    public void setImageResizeService(ImageResizeService imageResizeService) {
        this.imageResizeService = imageResizeService;
    }

    @Required
    public void setVideoConversionService(VideoConversionService videoConversionService) {
        this.videoConversionService = videoConversionService;
    }

    @Required
    public void setResizeDir(File resizeDir) {
        this.resizeDir = resizeDir;
    }

    public void setAllowedFileExtensions(Set<String> allowedFileExtensions) {
        this.allowedFileExtensions = allowedFileExtensions;
    }

    public void setMaxImageWidth(int maxImageWidth) {
        this.maxImageWidth = maxImageWidth;
    }

    public void setMaxImageHeight(int maxImageHeight) {
        this.maxImageHeight = maxImageHeight;
    }

    @Override
    public List<String> getRootDirectories() {
        List<String> rootDirCodes = new ArrayList<String>(galleryAuthorizationService.getRootPathsForCurrentUser().keySet());
        Collections.sort(rootDirCodes, directoryNameComparator);
        return rootDirCodes;
    }

    @Override
    public GalleryFile getImage(String publicPath, int width, int height) throws IOException, NotAllowedException {
        LOG.debug("Entering getImage(publicPath={}, width={}, height={}", publicPath, width, height);
        if (width <= 0 || width > maxImageWidth || height <= 0 || height > maxImageHeight) {
            String errorMessage = String.format("Non valid image size requested. Width: %s, height: %s", width, height);
            LOG.error(errorMessage);
            throw new IOException(errorMessage);
        }
        File realFile = getRealFileOrDir(publicPath);
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

    @Override
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

    @Override
    public List<String> getDirectories(String publicPath) throws IOException, NotAllowedException {
        File dir = getRealFileOrDir(publicPath);
        List<String> directoryPaths = new ArrayList<>();
        if (dir.isDirectory()) {
            for (File oneFile : dir.listFiles()) {
                if (oneFile.isDirectory()) {
                    StringBuilder onePathBuilder = new StringBuilder();
                    onePathBuilder.append(publicPath);
                    onePathBuilder.append(File.separator);
                    onePathBuilder.append(oneFile.getName());
                    directoryPaths.add(separatorsToUnix(onePathBuilder.toString()));
                }
            }
        }
        LOG.debug("Found {} directories for path {}", directoryPaths.size(), publicPath);
        Collections.sort(directoryPaths, directoryNameComparator);
        return directoryPaths;
    }

    @Override
    public List<GalleryFile> getAllVideos() throws IOException, NotAllowedException {
        Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
        Map<String, Collection<File>> rootPathVideos = new HashMap<>();
        for (Entry<String, File> oneEntry : rootPathsForCurrentUser.entrySet()) {
            List<File> videosForRootPath = listFiles(oneEntry.getValue(), fileFilter, FileFilterUtils.directoryFileFilter()).stream()
                    .filter(f -> isVideoNoException(f)).collect(Collectors.toList());
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

//    @Override
//    public Collection<File> getAllDirectories() throws IOException, NotAllowedException {
//        Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
//        Collection<File> allDirectories = new HashSet<>();
//        for (Entry<String, File> oneEntry : rootPathsForCurrentUser.entrySet()) {
//            allDirectories.addAll(listFilesAndDirs(oneEntry.getValue(), FileFilterUtils.falseFileFilter(), FileFilterUtils.directoryFileFilter()));
//        }
//        LOG.debug("Returning {} directories", allDirectories.size());
//        return allDirectories;
//    }

    @Override
    public GalleryFile getGalleryFile(String publicPath) throws IOException, NotAllowedException {
        LOG.debug("Entering getGalleryFile(publicPath={})", publicPath);
        File file = getRealFileOrDir(publicPath);
        if (file.isDirectory()) {
            throw new FileNotFoundException("Directories not allowed in this method!");
        }
        return createGalleryFile(publicPath, file);
    }

    @Override
    public List<String> getAvailableVideoModes() {
        List<String> availableVideoModes = new ArrayList<>(videoConversionService.getAvailableVideoModes());
        availableVideoModes.add(VIDEO_MODE_ORIGINAL);
        return availableVideoModes;
    }

    @Override
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

    @Override
    public File getRealFileOrDir(String publicPath) throws IOException, FileNotFoundException, NotAllowedException {
        LOG.debug("Entering getRealFileOrDir(publicPath={})", publicPath);
        if (StringUtils.isBlank(publicPath)) {
            throw new FileNotFoundException("Could not extract code from empty path!");
        }
        int index = publicPath.indexOf("/");
        if (index < 0) {
            index = publicPath.length();
        }
        String baseDirCode = publicPath.substring(0, index);
        LOG.debug("baseDirCode: {}", baseDirCode);
        File baseDir = galleryAuthorizationService.getRootPathsForCurrentUser().get(baseDirCode);
        if (baseDir == null) {
            String errorMessage = String.format("Could not find basedir for base dir code {}", baseDirCode);
            LOG.error(errorMessage);
            throw new FileNotFoundException(errorMessage);
        }
        File file = null;
        String relativePath = publicPath.substring(index, publicPath.length());
        LOG.debug("Relative path: {}", relativePath);
        if (StringUtils.isNotBlank(relativePath)) {
            file = new File(baseDir, relativePath);
            checkAllowed(baseDir, file);
        } else {
            // Don't need to check allowed on baseDir as this was just returned
            // from the authorization service.
            file = baseDir;
        }
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
     * <strong>NOTE! This method does NOT verify that the current user actually
     * has the right to access the given publicRoot! It is the responsibility of
     * calling methods to make sure only allowed root paths are used.</strong>
     *
     * @param publicRoot
     * @param file
     * @return The public path of the given file for the given publicRoot.
     * @throws IOException
     * @throws NotAllowedException
     */
    @Override
    public String getPublicPathFromRealFile(String publicRoot, File file) throws IOException, NotAllowedException {
        String actualFilePath = file.getCanonicalPath();
        File rootFile = galleryAuthorizationService.getRootPathsForCurrentUser().get(publicRoot);
        String relativePath = actualFilePath.substring(rootFile.getCanonicalPath().length(), actualFilePath.length());
        StringBuilder builder = new StringBuilder();
        builder.append(publicRoot);
        builder.append(relativePath);
        String publicPath = separatorsToUnix(builder.toString());
        LOG.debug("Actual file: {}, generated public path: {}", file, publicPath);
        return publicPath;
    }


    @Override
    public String getPublicRootFromRealFile(File file) throws IOException, NotAllowedException {
        Optional<Entry<String, File>> optRootEntry = galleryAuthorizationService.getRootPathsForCurrentUser().entrySet().stream().filter(e -> isFileParentOf(e.getValue(), file)).findAny();
        if (!optRootEntry.isPresent()) {
            String errorMessage = String.format("File %s was not part of any allowed root path!", file.getCanonicalPath());
            LOG.error(errorMessage);
            throw new IOException(errorMessage);
        }
        Entry<String, File> rootEntry = optRootEntry.get();
        //String publicPath = file.getCanonicalPath().replaceFirst(rootEntry.getValue().getCanonicalPath(), rootEntry.getKey());
        //LOG.debug("Actual file: {}, generated public path: {}", file, publicPath);
        //return publicPath;
        return rootEntry.getKey();
    }

    private boolean isFileParentOf(File possibleParent, File possibleChild) {
        File parentOfChild = possibleChild;
        do {
            if (possibleParent.equals(parentOfChild)) {
                return true;
            }
            parentOfChild = parentOfChild.getParentFile();
        } while (parentOfChild != null);
        return false;
    }

    @Override
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

    private String getContentType(File file) throws IOException {
        return Files.probeContentType(file.toPath());
    }

    private boolean isVideoNoException(File file) {
        try {
            return isVideo(file);
        } catch (IOException ioe) {
            return false;
        }
    }

    private boolean isVideo(File file) throws IOException {
        return StringUtils.startsWith(getContentType(file), "video");
    }

    private File determineResizedVideoImage(File originalFile, int width, int height) throws IOException {
        File resizedImage = determineResizedImage(originalFile, width, height);
        return new File(resizedImage.getCanonicalPath() + '.' + VIDEO_IMAGE_FILE_ENDING);
    }

    private File determineResizedImage(File originalFile, int width, int height) throws IOException {
        String resizePart = Integer.valueOf(width).toString() + "x" + Integer.valueOf(height).toString();
        File resizedImage = new File(resizeDir, File.separator + resizePart + File.separator + escapeFilePath(originalFile));
        return resizedImage;
    }

    private File determineConvertedVideo(File originalFile, String videoMode) throws IOException {
        File convertedVideo = new File(resizeDir, File.separator + videoMode + File.separator + escapeFilePath(originalFile));
        return convertedVideo;
    }

    /**
     * Small util method helping with escaping any characters that would not be allowed in a path. The obvious use case here is Windows and
     * it's drive letter followed by a ':'. Since the whole path will be appended to another root path that character is not allowed.
     *
     * @param file
     * @return
     * @throws IOException
     */
    private String escapeFilePath(File file) throws IOException {
        return file.getCanonicalPath().replace(":", "_");
    }

    private void checkAllowed(File baseDir, File fileToCheck) throws IOException, NotAllowedException {
        boolean allowed = galleryAuthorizationService.isAllowed(fileToCheck);
        if (!allowed) {
            throw new NotAllowedException("File " + fileToCheck + " not allowed!");
        }
    }

    private boolean isAllowedExtension(File file) {
        return allowedFileExtensions.contains(getExtension(file.getName()).toLowerCase());
    }

    private class CaseInsensitiveComparator implements Comparator<String> {

        @Override
        public int compare(String o1, String o2) {
            return o1.toLowerCase().compareTo(o2.toLowerCase());
        }
    }

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