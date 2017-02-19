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
package com.github.henkexbg.gallery.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.github.henkexbg.gallery.controller.model.ImageFormat;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;

import com.github.henkexbg.gallery.controller.exception.RangeException;
import com.github.henkexbg.gallery.controller.exception.ResourceNotFoundException;
import com.github.henkexbg.gallery.controller.model.GalleryFileHolder;
import com.github.henkexbg.gallery.controller.model.ListingContext;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.service.bean.GalleryFile;
import com.github.henkexbg.gallery.service.bean.GalleryFile.GalleryFileType;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

/**
 * This class receives requests where part of the path in the request URL is the
 * path of the resource (at least in a virtual sense).<br>
 * Actual service logic is delegated to service class. This class is more
 * concerned with handling web-related things, such as taking input from the
 * request, and adapting the response, including last modified checks.
 *
 * @author Henrik Bjerne
 *
 */
@Controller
public class GalleryController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private static final String DIR_LISTING_PREFIX = "/service/";

    private List<ImageFormat> imageFormats;

    private GalleryService galleryService;

    private boolean allowCustomImageSizes = false;

    @Required
    public void setGalleryService(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    public void setImageFormats(List<ImageFormat> imageFormats) {
        this.imageFormats = imageFormats;
    }

    public void setAllowCustomImageSizes(boolean allowCustomImageSizes) {
        this.allowCustomImageSizes = allowCustomImageSizes;
    }

    /**
     * Retrieves the listing for a given path (which can be empty). The response
     * can contain media in the shape of {@link GalleryFileHolder} instances as
     * well as sub-directories.
     *
     * @param servletRequest
     *            Servlet request
     * @param model
     *            Spring web model
     *
     * @return A {@link ListingContext} instance.
     * @throws IOException
     *             Sub-types of this exception are thrown for different
     *             scenarios, and the {@link IOException} itself for generic
     *             errors.
     */
    @RequestMapping(value = "/service/**", method = RequestMethod.GET)
    public
    @ResponseBody
    ListingContext getListing(HttpServletRequest servletRequest, Model model) throws IOException {
        String path = extractPathFromPattern(servletRequest);
        LOG.debug("Entering getListing(path={})", path);
        String contextPath = servletRequest.getContextPath();
        try {
            ListingContext listingContext = new ListingContext();
            listingContext.setAllowCustomImageSizes(allowCustomImageSizes);
            listingContext.setImageFormats(imageFormats);
            listingContext.setVideoFormats(galleryService.getAvailableVideoModes());
            if (StringUtils.isBlank(path)) {
                listingContext.setDirectories(generateUrlsFromDirectoryPaths(path, contextPath, galleryService.getRootDirectories()));
            } else {
                listingContext.setCurrentPathDisplay(path);
                listingContext.setPreviousPath(getPreviousPath(contextPath, path));
                List<GalleryFile> directoryListing = galleryService.getDirectoryListingFiles(path);
                if (directoryListing == null) {
                    throw new ResourceNotFoundException();
                }
                LOG.debug("{} media files found", directoryListing.size());
                List<GalleryFile> galleryImages = directoryListing.stream().filter(gi -> GalleryFileType.IMAGE.equals(gi.getType()))
                        .collect(Collectors.toList());
                List<GalleryFileHolder> listing = convertToGalleryFileHolders(contextPath, galleryImages);
                listingContext.setImages(listing);
                List<GalleryFile> galleryVideos = directoryListing.stream().filter(gi -> GalleryFileType.VIDEO.equals(gi.getType()))
                        .collect(Collectors.toList());
                List<GalleryFileHolder> videoHolders = convertToGalleryFileHolders(contextPath, galleryVideos);
                listingContext.setVideos(videoHolders);
                listingContext.setDirectories(generateUrlsFromDirectoryPaths(path, contextPath, galleryService.getDirectories(path)));
            }
            return listingContext;
        } catch (NotAllowedException noe) {
            LOG.warn("Not allowing resource {}", path);
            throw new ResourceNotFoundException();
        } catch (FileNotFoundException fnfe) {
            LOG.warn("Could not find resource {}", path);
            throw new ResourceNotFoundException();
        } catch (IOException ioe) {
            LOG.error("Error when calling getImage", ioe);
            throw ioe;
        }
    }

    /**
     * Requests an image with the given {@link ImageFormat}.
     *
     * @param request
     *            Spring request
     * @param servletRequest
     *            Servlet request
     * @param imageFormatCode
     *            Image format.
     *
     * @return The image as a stream with the appropriate response headers set
     *         or a not-modified response, (see
     *         {@link #returnResource(WebRequest, GalleryFile)}).
     * @throws IOException
     *             Sub-types of this exception are thrown for different
     *             scenarios, and the {@link IOException} itself for generic
     *             errors.
     */
    @RequestMapping(value = "/image/{imageFormat}/**", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getImage(WebRequest request, HttpServletRequest servletRequest,
                                                        @PathVariable(value = "imageFormat") String imageFormatCode) throws IOException {
        String path = extractPathFromPattern(servletRequest);
        LOG.debug("getImage(imageFormatCode={}, path={})", imageFormatCode, path);
        try {
            ImageFormat imageFormat = getImageFormatForCode(imageFormatCode);
            if (imageFormat == null) {
                throw new ResourceNotFoundException();
            }
            GalleryFile galleryFile = galleryService.getImage(path, imageFormat.getWidth(), imageFormat.getHeight());
            return returnResource(request, galleryFile);
        } catch (FileNotFoundException fnfe) {
            LOG.warn("Could not find resource {}", path);
            throw new ResourceNotFoundException();
        } catch (NotAllowedException nae) {
            LOG.warn("User was not allowed to access resource {}", path);
            throw new ResourceNotFoundException();
        } catch (IOException ioe) {
            LOG.error("Error when calling getImage", ioe);
            throw ioe;
        }
    }

    /**
     * Requests an image of a custom size. This method will return the image
     * only if {@link #allowCustomImageSizes} is set to true.
     *
     * @param request
     *            Spring request
     * @param servletRequest
     *            Servlet request
     * @param width
     *            Width in pixels
     * @param height
     *            Height in pixels
     *
     * @return The image as a stream with the appropriate response headers set
     *         or a not-modified response, (see
     *         {@link #returnResource(WebRequest, GalleryFile)}).
     * @throws IOException
     *             Sub-types of this exception are thrown for different
     *             scenarios, and the {@link IOException} itself for generic
     *             errors.
     */
    @RequestMapping(value = "/customImage/{width}/{height}/**", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getCustomImage(WebRequest request, HttpServletRequest servletRequest,
                                                              @PathVariable(value = "width") String width, @PathVariable(value = "height") String height) throws IOException {
        if (!allowCustomImageSizes) {
            LOG.debug("Request for custom image was made despite allowCustomImageSizes being false.");
            throw new ResourceNotFoundException();
        }
        String path = extractPathFromPattern(servletRequest);
        LOG.debug("getCustomImage(width={}, height={}, path={})", width, height, path);
        try {
            int widthInt = Integer.parseInt(width);
            int heightInt = Integer.parseInt(height);
            if (widthInt <= 0 || heightInt <= 0) {
                LOG.debug("Won't try to scale an image do negative dimensions...", path);
                throw new ResourceNotFoundException();
            }
            GalleryFile galleryFile = galleryService.getImage(path, widthInt, heightInt);
            return returnResource(request, galleryFile);
        } catch (FileNotFoundException fnfe) {
            LOG.warn("Could not find resource {}", path);
            throw new ResourceNotFoundException();
        } catch (NotAllowedException nae) {
            LOG.warn("User was not allowed to access resource {}", path);
            throw new ResourceNotFoundException();
        } catch (NumberFormatException nfe) {
            LOG.warn("Could not parse image dimensions {}", path);
            throw new ResourceNotFoundException();
        } catch (IOException ioe) {
            LOG.error("Error when calling getImage", ioe);
            throw ioe;
        }
    }

    /**
     * Requests a video of a certain format.
     *
     * @param request
     *            Spring request
     * @param servletRequest
     *            Servlet request
     * @param conversionFormat
     *            Video format
     *
     * @return The image as a stream with the appropriate response headers set
     *         or a not-modified response, (see
     *         {@link #returnResource(WebRequest, GalleryFile)}).
     *
     * @throws IOException
     *             Sub-types of this exception are thrown for different
     *             scenarios, and the {@link IOException} itself for generic
     *             errors.
     */
    @RequestMapping(value = "/video/{conversionFormat}/**", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getVideo(WebRequest request, HttpServletRequest servletRequest,
                                                        @PathVariable(value = "conversionFormat") String conversionFormat) throws IOException {
        String path = extractPathFromPattern(servletRequest);
        LOG.debug("getVideo(path={}, conversionFormat={})", path, conversionFormat);
        try {
            // GalleryFile galleryFile = galleryService.getGalleryFile(path);
            GalleryFile galleryFile = galleryService.getVideo(path, conversionFormat);
            if (!GalleryFileType.VIDEO.equals(galleryFile.getType())) {
                LOG.warn("File {} was not a video but {}. Throwing ResourceNotFoundException.", path, galleryFile.getType());
                throw new ResourceNotFoundException();
            }
            return returnResource(request, galleryFile);
        } catch (FileNotFoundException fnfe) {
            LOG.warn("Could not find resource {}", path);
            throw new ResourceNotFoundException();
        } catch (NotAllowedException nae) {
            LOG.warn("User was not allowed to access resource {}", path);
            throw new ResourceNotFoundException();
        } catch (IOException ioe) {
            LOG.error("Error when calling getVideo", ioe);
            throw ioe;
        }
    }

    /**
     * Method used to return the binary of a gallery file (
     * {@link GalleryFile#getActualFile()} ). This method handles 304 redirects
     * (if file has not changed) and range headers if requested by browser. The
     * range parts is particularly important for videos. The correct response
     * status is set depending on the circumstances.
     * <p>
     * NOTE: the range logic should NOT be considered a complete implementation
     * - it's a bare minimum for making requests for byte ranges work.
     *
     * @param request
     *            Request
     * @param galleryFile
     *            Gallery file
     * @return The binary of the gallery file, or a 304 redirect, or a part of
     *         the file.
     * @throws IOException
     *             If there is an issue accessing the binary file.
     */
    private ResponseEntity<InputStreamResource> returnResource(WebRequest request, GalleryFile galleryFile) throws IOException {
        LOG.debug("Entering returnResource()");
        if (request.checkNotModified(galleryFile.getActualFile().lastModified())) {
            return null;
        }
        File file = galleryFile.getActualFile();
        String contentType = galleryFile.getContentType();
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        long[] ranges = getRangesFromHeader(rangeHeader);
        long startPosition = ranges[0];
        long fileTotalSize = file.length();
        long endPosition = ranges[1] != 0 ? ranges[1] : fileTotalSize - 1;
        long contentLength = endPosition - startPosition + 1;
        LOG.debug("contentLength: {}, file length: {}", contentLength, fileTotalSize);

        LOG.debug("Returning resource {} as inputstream. Start position: {}", file.getCanonicalPath(), startPosition);
        InputStream boundedInputStream = new BoundedInputStream(new FileInputStream(file), endPosition + 1);

        InputStream is = new BufferedInputStream(boundedInputStream, 65536);
        InputStreamResource inputStreamResource = new InputStreamResource(is);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentLength(contentLength);
        responseHeaders.setContentType(MediaType.valueOf(contentType));
        responseHeaders.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (StringUtils.isNotBlank(rangeHeader)) {
            is.skip(startPosition);
            String contentRangeResponseHeader = "bytes " + startPosition + "-" + endPosition + "/" + fileTotalSize;
            responseHeaders.add(HttpHeaders.CONTENT_RANGE, contentRangeResponseHeader);
            LOG.debug("{} was not null but {}. Adding header {} to response: {}", HttpHeaders.RANGE, rangeHeader,
                    HttpHeaders.CONTENT_RANGE, contentRangeResponseHeader);
        }
        HttpStatus status = (startPosition == 0 && contentLength == fileTotalSize) ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
        LOG.debug("Returning {}. Status: {}, content-type: {}, {}: {}, contentLength: {}", file, status, contentType,
                HttpHeaders.CONTENT_RANGE, responseHeaders.get(HttpHeaders.CONTENT_RANGE), contentLength);
        return new ResponseEntity<InputStreamResource>(inputStreamResource, responseHeaders, status);
    }

    /**
     * Extracts the request range header if present.
     *
     * @param rangeHeader
     *            Range header.
     * @return a long[] which will always be the size 2. The first element is
     *         the start index, and the second the end index. If the end index
     *         is not set (which means till the end of the resource), 0 is
     *         returned in that field.
     */
    private long[] getRangesFromHeader(String rangeHeader) {
        LOG.debug("Range header: {}", rangeHeader);
        long[] result = new long[2];
        final String headerPrefix = "bytes=";
        if (StringUtils.startsWith(rangeHeader, headerPrefix)) {
            String[] splitRange = rangeHeader.substring(headerPrefix.length()).split("-");
            try {
                result[0] = Long.parseLong(splitRange[0]);
                if (splitRange.length > 1) {
                    result[1] = Long.parseLong(splitRange[1]);
                }
                if (result[0] < 0 || (result[1] != 0 && result[0] > result[1])) {
                    throw new RangeException();
                }
            } catch (NumberFormatException nfe) {
                throw new RangeException();
            }
        }
        return result;
    }

    /**
     * Helper method that retrieves the URL to the previous path if available.
     * Previous in this case always means one step up in the hierarchy. It is
     * assumed that this is always a directory i.e. the URL will point to the
     * {@value #DIR_LISTING_PREFIX} service.
     *
     * @param contextPath
     *            Webapp context path.
     * @param path
     *            Webapp-specific path.
     * @return The previous path, or null.
     */
    private String getPreviousPath(String contextPath, String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        int lastIndexOfSlash = path.lastIndexOf('/');
        if (lastIndexOfSlash == -1) {
            return null;
        }
        return contextPath + DIR_LISTING_PREFIX + path.substring(0, lastIndexOfSlash);
    }

    /**
     * Will return each directory as a map entry. The key will be a nicer
     * display name of the directory (just the directory name without the path).<br>
     * The value will be the public directory path (such as returned from the
     * {@link GalleryService}) prepended by the context path and service path.
     *
     * @param currentPath
     *            Webapp-specific path.
     * @param contextPath
     *            Webapp context path.
     * @param directoryPaths
     *            Directory paths.
     * @return A Map where each key is a directory name for display, and each
     *         value is the URL for that directory listing.
     */
    private Map<String, String> generateUrlsFromDirectoryPaths(String currentPath, String contextPath, List<String> directoryPaths) {
        Map<String, String> urls = new TreeMap<>();
        for (String oneDir : directoryPaths) {
            String oneDirName = StringUtils.isNotBlank(currentPath) && StringUtils.startsWith(oneDir, currentPath) ? oneDir
                    .substring(currentPath.length() + 1) : oneDir;
            urls.put(oneDirName, contextPath + DIR_LISTING_PREFIX + oneDir);
        }
        return urls;
    }

    /**
     * Converts the provided service layer {@link GalleryFile} objects to web
     * model {@link GalleryFileHolder} objects.
     *
     * @param contextPath
     *            Webapp context path.
     * @param galleryFiles
     *            List of gallery files.
     * @return A list of gallery files holders
     */
    private List<GalleryFileHolder> convertToGalleryFileHolders(String contextPath, List<GalleryFile> galleryFiles) {
        List<GalleryFileHolder> galleryFileHolders = new ArrayList<>();
        for (GalleryFile oneGalleryFile : galleryFiles) {
            GalleryFileHolder oneGalleryFileHolder = new GalleryFileHolder();
            oneGalleryFileHolder.setFilename(oneGalleryFile.getActualFile().getName());
            if (GalleryFileType.IMAGE.equals(oneGalleryFile.getType())) {
                oneGalleryFileHolder.setFreeSizePath(generateCustomImageUrlTemplate(contextPath, oneGalleryFile));
                oneGalleryFileHolder.setFormatPath(generateDynamicImageUrl(contextPath, oneGalleryFile));
            } else {
                oneGalleryFileHolder.setFormatPath(contextPath + "/video/{conversionFormat}/" + oneGalleryFile.getPublicPath());
            }
            oneGalleryFileHolder.setContentType(oneGalleryFile.getContentType());
            galleryFileHolders.add(oneGalleryFileHolder);
        }
        return galleryFileHolders;
    }

    /**
     * Generates the URL template for a certain image format.
     *
     * @param contextPath
     *            Webapp context path.
     * @param file
     *            Image.
     * @return The URL for the image at the given image format code.
     */
    private String generateDynamicImageUrl(String contextPath, GalleryFile file) {
        return contextPath + "/image/{imageFormat}/" + file.getPublicPath();
    }

    /**
     * Generates the URL template for a certain image size.
     *
     * @param contextPath
     *            Webapp context path.
     * @param file
     *            Image.
     * @return The URL template for the image at the given image format code.
     *         The URL will contain the placeholders {width} and {height}. The
     *         idea with these is that a calling entitiy should swap those
     *         values for the actual width/height.
     */
    private String generateCustomImageUrlTemplate(String contextPath, GalleryFile file) {
        return contextPath + "/customImage/{width}/{height}/" + file.getPublicPath();

    }

    /**
     * Due to some Spring MVC oddities with pattern matching, the following
     * method was put in place to correctly extract the path from the URL path
     * (remember, the URL path contains more information than just the image
     * path).
     *
     * @param request
     *            Request
     * @return The public image path.
     */
    private String extractPathFromPattern(final HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        AntPathMatcher apm = new AntPathMatcher();
        String finalPath = apm.extractPathWithinPattern(bestMatchPattern, path);
        return finalPath;
    }

    /**
     * Retrieves the image format for the image format code.
     *
     * @param code
     *            Code.
     * @return The {@link ImageFormat}, or null.
     */
    private ImageFormat getImageFormatForCode(String code) {
        if (code == null || imageFormats == null) {
            return null;
        }
        for (ImageFormat oneImageFormat : imageFormats) {
            if (code.equalsIgnoreCase(oneImageFormat.getCode())) {
                return oneImageFormat;
            }
        }
        return null;
    }

}