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

import com.github.henkexbg.gallery.bean.GalleryDirectory;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.service.ImageResizeService;
import com.github.henkexbg.gallery.service.VideoConversionService;
import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.bean.GalleryFile.GalleryFileType;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

import javax.annotation.PostConstruct;

/**
 * Implementation of the {@link GalleryService} interface. A number of services
 * are tied together via this class, such as services for authorization,
 * resizing and conversion. In addition, this class defines a number of file
 * formats it will accept. Any other formats will be disregarded even if the
 * files are allowed in terms of location.
 *
 * @author Henrik Bjerne
 */
public class GalleryServiceImpl implements GalleryService {

	public static final String VIDEO_MODE_ORIGINAL = "ORIGINAL";

	public static final String DEFAULT_IMAGE_FILE_ENDING = "jpg";

	public static final String DIR_IMAGE_DIR_NAME = "_directoryImages_";

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private GalleryAuthorizationService galleryAuthorizationService;

	private ImageResizeService imageResizeService;

	private VideoConversionService videoConversionService;

	private File resizeDir;

	private Set<String> allowedFileExtensions;

	private final IOFileFilter fileFilter = new CaseInsensitiveFileEndingFilter();

	private int maxImageWidth = 5000;

	private int maxImageHeight = 5000;

	private int directoryImageMaxAgeMinutes = 1440;

	private File dirImageDir;

	@PostConstruct
	public void setUp() {
		dirImageDir = new File(resizeDir, DIR_IMAGE_DIR_NAME);
		if (!dirImageDir.exists()) {
			dirImageDir.mkdir();
		}
	}

	public void setGalleryAuthorizationService(GalleryAuthorizationService galleryAuthorizationService) {
		this.galleryAuthorizationService = galleryAuthorizationService;
	}

	public void setImageResizeService(ImageResizeService imageResizeService) {
		this.imageResizeService = imageResizeService;
	}

	public void setVideoConversionService(VideoConversionService videoConversionService) {
		this.videoConversionService = videoConversionService;
	}

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

	public void setDirectoryImageMaxAgeMinutes(int directoryImageMaxAgeMinutes) {
		this.directoryImageMaxAgeMinutes = directoryImageMaxAgeMinutes;
	}

	@Override
	public List<GalleryDirectory> getRootDirectories() throws IOException, NotAllowedException {
		List<String> rootDirCodes = new ArrayList<String>(
				galleryAuthorizationService.getRootPathsForCurrentUser().keySet());
		List<GalleryDirectory> galleryDirectories = new ArrayList<>(rootDirCodes.size());
		Collections.sort(rootDirCodes, String.CASE_INSENSITIVE_ORDER);
		for (String oneRootDirCode : rootDirCodes) {
			File oneRootDir = getRealFileOrDir(oneRootDirCode);
			galleryDirectories.add(createGalleryDirectory(oneRootDirCode, oneRootDir, oneRootDirCode));
		}
		return galleryDirectories;
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
				String errorMessage = String.format("Main realFile %s did not exist. Could not resize.",
						realFile.getCanonicalPath());
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
	public List<GalleryDirectory> getDirectories(String publicPath) throws IOException, NotAllowedException {
		File dir = getRealFileOrDir(publicPath);
		List<GalleryDirectory> galleryDirectories = new ArrayList<>();
		if (dir.isDirectory()) {
			List<File> directories = Arrays.asList(dir.listFiles(File::isDirectory));
			LOG.debug("Found {} directories for path {}", directories.size(), publicPath);
			Collections.sort(directories, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
			for (File oneDir : directories) {
				String oneDirPublicPath = buildPublicPathForFileInPublicDir(publicPath, oneDir);
				galleryDirectories.add(createGalleryDirectory(oneDirPublicPath, oneDir));
			}
		}
		return galleryDirectories;
	}

	@Override
	public List<GalleryFile> getAllVideos() throws IOException, NotAllowedException {
		Map<String, File> rootPathsForCurrentUser = galleryAuthorizationService.getRootPathsForCurrentUser();
		Map<String, Collection<File>> rootPathVideos = new HashMap<>();
		for (Entry<String, File> oneEntry : rootPathsForCurrentUser.entrySet()) {
			List<File> videosForRootPath = listFiles(oneEntry.getValue(), fileFilter,
					FileFilterUtils.directoryFileFilter()).stream().filter(f -> isVideoNoException(f))
							.collect(Collectors.toList());
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
					String errorMessage = String.format("Main video %s did not exist. Could not resize.",
							video.getCanonicalPath());
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
	private File getRealFileOrDir(String publicPath) throws IOException, FileNotFoundException, NotAllowedException {
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
			if (!galleryAuthorizationService.isAllowed(file)) {
				throw new NotAllowedException("File " + file + " not allowed!");
			}
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
		String relativePath = actualFilePath.substring(rootFile.getCanonicalPath().length(), actualFilePath.length());
		StringBuilder builder = new StringBuilder();
		builder.append(publicRoot);
		builder.append(relativePath);
		String publicPath = separatorsToUnix(builder.toString());
		LOG.debug("Actual file: {}, generated public path: {}", file, publicPath);
		return publicPath;
	}

	/**
	 * Creates a {@link GalleryDirectory} given the public path and the actual
	 * directory. Public path is required as multiple public paths can point to the
	 * same actual directory.
	 * 
	 * @param publicPath Public path.
	 * @param actualDir  Directory.
	 * @param dirName Name of gallery directory to be created
	 * @return A {@link GalleryDirectory} for the given parameters
	 * @throws IOException
	 */
	private GalleryDirectory createGalleryDirectory(String publicPath, File actualDir, String dirName) throws IOException {
		GalleryDirectory galleryDirectory = new GalleryDirectory();
		galleryDirectory.setPublicPath(publicPath);
		galleryDirectory.setName(dirName);
		File directoryImage = getDirectoryImage(actualDir);
		if (directoryImage != null) {
			// Use the public path of the directory, and combine it with the image
			galleryDirectory.setImage(createGalleryFile(publicPath, directoryImage));
		}
		return galleryDirectory;
	}
	
	/**
	 * As {@link #createGalleryDirectory(String, File, String)}, but uses the directory name as gallery directory name.
	 * 
	 * @param publicPath Public path.
	 * @param actualDir  Directory.
	 * @return A {@link GalleryDirectory} for the given parameters
	 * @throws IOException
	 */
	private GalleryDirectory createGalleryDirectory(String publicPath, File actualDir) throws IOException {
		return createGalleryDirectory(publicPath, actualDir, actualDir.getName());
	}

	/**
	 * Retrieves the image for a directory. If necessary the image will be generated
	 * first.
	 * 
	 * @param directory Directory
	 * @return The generated image, or null if no image could be generated, for
	 *         example because there are no images in the directory.
	 * @throws IOException
	 */
	private File getDirectoryImage(File directory) throws IOException {
		File directoryImage = determineDirectoryImage(directory);
		if (!directoryImage.exists()
				|| directoryImage.lastModified() < System.currentTimeMillis() - (directoryImageMaxAgeMinutes * 60000)) {
			LOG.debug("Evaluating directory image for {}", directory);
			List<File> imagesForCompositeDirectoryImage = findImagesForCompositeDirectoryImage(directory);
			if (!imagesForCompositeDirectoryImage.isEmpty()) {
				long newestSourceImageTimestamp = imagesForCompositeDirectoryImage.stream()
						.sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified())).findFirst().get()
						.lastModified();
				if (directoryImage.exists() && newestSourceImageTimestamp < directoryImage.lastModified()) {
					// Extra optimization. If the directory image has expired, but the composite
					// images are not newer
					// than the current directory image, just update the last modified on the
					// directory image
					LOG.debug("Keeping expired directory image, renewing timestamp");
					directoryImage.setLastModified(System.currentTimeMillis());
				} else {
					LOG.debug("Will generate new composite image for directory {}", directoryImage);
					try {
						imageResizeService.generateCompositeImage(imagesForCompositeDirectoryImage, directoryImage,
								maxImageWidth, maxImageHeight);
					} catch (IOException ioe) {
						String errorMessage = String.format(
								"Error when generating composite image for %s. Returning null.",
								directory.getCanonicalPath());
						LOG.error(errorMessage, ioe);
						directoryImage = null;
					}
				}
			} else {
				// Create empty file so that we can check the timestamp towards it and not
				// always try to generate a new
				// file for directories without images.
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
	 *         May return empty list if none found
	 */
	private List<File> findImagesForCompositeDirectoryImage(File directory) {
		final int nrImages = 4;
		List<File> foundFiles = listFiles(directory, fileFilter, null).stream().filter(f -> !isVideoNoException(f))
				.collect(Collectors.toList());
		if (foundFiles.size() >= nrImages) {
			return foundFiles;
		}
		File[] directories = directory.listFiles(File::isDirectory);
		if (directories != null) {
			int i = 0;
			while (i < directories.length && foundFiles.size() < nrImages) {
				foundFiles.addAll(listFiles(directories[i], fileFilter, null).stream()
						.filter(f -> !isVideoNoException(f)).collect(Collectors.toList()));
				i++;
			}
		}
		return foundFiles.subList(0, Math.min(nrImages, foundFiles.size()));
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
	private GalleryFile createGalleryFile(String publicPath, File actualFile) throws IOException {
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
		File resizedImage = new File(resizeDir, File.separator + resizePart + File.separator
				+ escapeFilePath(originalFile) + (originalFile.isDirectory() ? '.' + DEFAULT_IMAGE_FILE_ENDING : ""));
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
		File convertedVideo = new File(resizeDir,
				File.separator + videoMode + File.separator + escapeFilePath(originalFile));
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
		String filename = directory.getName() + '-' + directory.getCanonicalPath().hashCode() + '.'
				+ DEFAULT_IMAGE_FILE_ENDING;
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
	 * Checks whether file has an allowed file extension. Checked towards
	 * {@link #allowedFileExtensions} in a case insensitive way.
	 * 
	 * @param file File
	 * @return True if allowed
	 */
	private boolean isAllowedExtension(File file) {
		return allowedFileExtensions.contains(getExtension(file.getName()).toLowerCase());
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