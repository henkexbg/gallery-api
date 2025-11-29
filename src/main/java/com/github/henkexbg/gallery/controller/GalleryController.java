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
package com.github.henkexbg.gallery.controller;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.github.henkexbg.gallery.bean.SearchResult;
import com.github.henkexbg.gallery.service.GallerySearchService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import com.github.henkexbg.gallery.bean.GalleryDirectory;
import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.bean.GalleryFile.GalleryFileType;
import com.github.henkexbg.gallery.controller.exception.RangeException;
import com.github.henkexbg.gallery.controller.exception.ResourceNotFoundException;
import com.github.henkexbg.gallery.controller.model.GalleryDirectoryHolder;
import com.github.henkexbg.gallery.controller.model.GalleryFileHolder;
import com.github.henkexbg.gallery.controller.model.ImageFormat;
import com.github.henkexbg.gallery.controller.model.ListingContext;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

/**
 * This class contains the actual web endpoints for this application. Most of the logic revolves around extracting parameters, calling the
 * appropriate methods on {@link GalleryService} (which deals with all the business logic), and then returning the relevant data. Most
 * methods deal with returning JSON responses, but there are also endpoints for returning images or ranged parts of a video.<br> Actual
 * service logic is delegated to service class. This class is more concerned with handling web-related things, such as taking input from the
 * request, and adapting the response, including last modified checks.
 *
 * @author Henrik Bjerne
 *
 */
@RestController
public class GalleryController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private static final String SERVICE_PATH = "/service/";

    @Resource
    private List<ImageFormat> imageFormats;

    @Resource
    private GalleryService galleryService;

    @Resource
    private GallerySearchService gallerySearchService;

    @Value("${gallery.allowCustomImageSizes}")
    private boolean allowCustomImageSizes = false;

    @Value("${gallery.mediaResourcesCacheHeader}")
    private String mediaResourcesCacheHeader;

    /**
     * Retrieves the listing for a given path (which can be empty). The response can contain media in the shape of {@link GalleryFileHolder}
     * instances as well as subdirectories.
     *
     * @param servletRequest Servlet request
     * @return A {@link ListingContext} instance.
     * @throws IOException Subtypes of this exception are thrown for different scenarios, and the {@link IOException} itself for generic
     *                     errors.
     */
    @GetMapping("/service/{*filePath}")
    public ListingContext query(HttpServletRequest servletRequest, @PathVariable String filePath,
                                              @RequestParam(required = false, value = "searchTerm") String searchTerm) throws Exception {
        long startTime = System.currentTimeMillis();
        // Extracted public path starts with '/', public path does not
        String publicPath = filePath.substring(1);
        String contextPath = servletRequest.getContextPath();
        LOG.debug("Entering getListing(path={})", publicPath);
        ListingContext listingContext = new ListingContext();
        listingContext.setAllowCustomImageSizes(allowCustomImageSizes);
        listingContext.setImageFormats(imageFormats);
        listingContext.setVideoFormats(galleryService.getAvailableVideoModes());
        SearchResult searchResult = gallerySearchService.search(publicPath, searchTerm);
        listingContext.setMedia(convertToGalleryFileHolders(contextPath, searchResult.files()));
        listingContext.setDirectories(convertToGalleryDirectoryHolders(contextPath, searchResult.directories()));
        LOG.debug("Found {} media files, {} directories in {} milliseconds", searchResult.files(), searchResult.directories().size(),
                System.currentTimeMillis() - startTime);
        return listingContext;
    }

    /**
     * Requests an image with the given {@link ImageFormat}.
     *
     * @param request         Spring request
     * @param imageFormatCode Image format.
     * @return The image as a stream with the appropriate response headers set or a not-modified response, (see
     * {@link #returnResource(WebRequest, GalleryFile)}).
     * @throws IOException Sub-types of this exception are thrown for different scenarios, and the {@link IOException} itself for generic
     *                     errors.
     */
    @RequestMapping(value = "/image/{imageFormat}/{*filePath}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getImage(WebRequest request, @PathVariable(value = "imageFormat") String imageFormatCode,
                                                        @PathVariable String filePath) throws IOException, NotAllowedException {
        String path = filePath.substring(1);
        LOG.debug("getImage(imageFormatCode={}, path={})", imageFormatCode, path);
        ImageFormat imageFormat = getImageFormatForCode(imageFormatCode);
        if (imageFormat == null) {
            throw new ResourceNotFoundException();
        }
        GalleryFile galleryFile = galleryService.getImage(path, imageFormat.getWidth(), imageFormat.getHeight());
        return returnResource(request, galleryFile);
    }

    /**
     * Requests an image of a custom size. This method will return the image only if {@link #allowCustomImageSizes} is set to true.
     *
     * @param request Spring request
     * @param width   Width in pixels
     * @param height  Height in pixels
     * @return The image as a stream with the appropriate response headers set or a not-modified response, (see
     * {@link #returnResource(WebRequest, GalleryFile)}).
     * @throws IOException Sub-types of this exception are thrown for different scenarios, and the {@link IOException} itself for generic
     *                     errors.
     */
    @RequestMapping(value = "/customImage/{width}/{height}/{*filePath}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getCustomImage(WebRequest request, @PathVariable(value = "width") String width,
                                                              @PathVariable(value = "height") String height, @PathVariable String filePath)
            throws IOException, NotAllowedException {
        if (!allowCustomImageSizes) {
            LOG.debug("Request for custom image was made despite allowCustomImageSizes being false.");
            throw new ResourceNotFoundException();
        }
        String path = filePath.substring(1);
        LOG.debug("getCustomImage(width={}, height={}, path={})", width, height, path);
        try {
            int widthInt = Integer.parseInt(width);
            int heightInt = Integer.parseInt(height);
            if (widthInt <= 0 || heightInt <= 0) {
                String errorMessage = "Can't scale an image to negative dimensions for %s".formatted(path);
                LOG.debug(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
            GalleryFile galleryFile = galleryService.getImage(path, widthInt, heightInt);
            return returnResource(request, galleryFile);
        } catch (NumberFormatException nfe) {
            String errorMessage = "Could not parse image dimensions %s".formatted(path);
            LOG.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Requests a video of a certain format.
     *
     * @param request          Spring request
     * @param conversionFormat Video format
     * @return The image as a stream with the appropriate response headers set or a not-modified response, (see
     * {@link #returnResource(WebRequest, GalleryFile)}).
     * @throws IOException Sub-types of this exception are thrown for different scenarios, and the {@link IOException} itself for generic
     *                     errors.
     */
    @RequestMapping(value = "/video/{conversionFormat}/{*filePath}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> getVideo(WebRequest request,
                                                        @PathVariable(value = "conversionFormat") String conversionFormat,
                                                        @PathVariable String filePath) throws IOException, NotAllowedException {
        String path = filePath.substring(1);
        LOG.debug("getVideo(path={}, conversionFormat={})", path, conversionFormat);

        GalleryFile galleryFile = galleryService.getVideo(path, conversionFormat);
        if (!GalleryFileType.VIDEO.equals(galleryFile.getType())) {
            LOG.warn("File {} was not a video but {}. Throwing ResourceNotFoundException.", path, galleryFile.getType());
            throw new ResourceNotFoundException();
        }
        return returnResource(request, galleryFile);
    }

    /**
     * Method used to return the binary of a gallery file ( {@link GalleryFile#getActualFile()} ). This method handles 304 redirects (if
     * file has not changed) and range headers if requested by browser. The range parts is particularly important for videos. The correct
     * response status is set depending on the circumstances.
     * <p>
     * NOTE: the range logic should NOT be considered a complete implementation - it's a bare minimum for making requests for byte ranges
     * work.
     *
     * @param request     Request
     * @param galleryFile Gallery file
     * @return The binary of the gallery file, or a 304 redirect, or a part of the file.
     * @throws IOException If there is an issue accessing the binary file.
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
        responseHeaders.setCacheControl(mediaResourcesCacheHeader);
        responseHeaders.setContentLength(contentLength);
        responseHeaders.setContentType(MediaType.valueOf(contentType));
        responseHeaders.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (StringUtils.isNotBlank(rangeHeader)) {
            is.skip(startPosition);
            String contentRangeResponseHeader = "bytes " + startPosition + "-" + endPosition + "/" + fileTotalSize;
            responseHeaders.add(HttpHeaders.CONTENT_RANGE, contentRangeResponseHeader);
            LOG.debug("{} was not null but {}. Adding header {} to response: {}", HttpHeaders.RANGE, rangeHeader, HttpHeaders.CONTENT_RANGE,
                    contentRangeResponseHeader);
        }
        HttpStatus status = (startPosition == 0 && contentLength == fileTotalSize) ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
        LOG.debug("Returning {}. Status: {}, content-type: {}, {}: {}, contentLength: {}", file, status, contentType,
                HttpHeaders.CONTENT_RANGE, responseHeaders.get(HttpHeaders.CONTENT_RANGE), contentLength);
        return new ResponseEntity<>(inputStreamResource, responseHeaders, status);
    }

    /**
     * Extracts the request range header if present.
     *
     * @param rangeHeader Range header.
     * @return a long[] which will always be the size 2. The first element is the start index, and the second the end index. If the end
     * index is not set (which means till the end of the resource), 0 is returned in that field.
     */
    private long[] getRangesFromHeader(String rangeHeader) {
        LOG.debug("Range header: {}", rangeHeader);
        long[] result = new long[2];
        final String headerPrefix = "bytes=";
        if (Strings.CS.startsWith(rangeHeader, headerPrefix)) {
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
     * Converts a list of service layer {@link GalleryFile} objects to web model {@link GalleryFileHolder} objects.
     *
     * @param contextPath  Webapp context path.
     * @param galleryFiles List of gallery files.
     * @return A list of gallery files holders
     */
    private List<GalleryFileHolder> convertToGalleryFileHolders(String contextPath, List<GalleryFile> galleryFiles) {
        List<GalleryFileHolder> galleryFileHolders = new ArrayList<>(galleryFiles.size());
        galleryFiles.forEach(gf -> galleryFileHolders.add(convertToGalleryFileHolder(contextPath, gf)));
        return galleryFileHolders;
    }

    /**
     * Converts the provided service layer {@link GalleryFile} object to a web model {@link GalleryFileHolder} object. The public paths are
     * appended to the appropriate services in question; a video for example is retrieved via a different URL than an image, and images are
     * retrieved from different URLs depending on whether they are free size URLs or requested with a specific format.
     *
     * @param contextPath Webapp context path.
     * @param galleryFile Gallery file.
     * @return A gallery file holder
     */
    private GalleryFileHolder convertToGalleryFileHolder(String contextPath, GalleryFile galleryFile) {
        GalleryFileHolder galleryFileHolder = new GalleryFileHolder();
        galleryFileHolder.setFilename(galleryFile.getActualFile().getName());
        if (allowCustomImageSizes) {
            galleryFileHolder.setFreeSizePath(generateCustomImageUrlTemplate(contextPath, galleryFile));
        }
        galleryFileHolder.setFormatPath(generateDynamicImageUrl(contextPath, galleryFile));
        if (GalleryFileType.VIDEO.equals(galleryFile.getType())) {
            galleryFileHolder.setVideoPath(contextPath + "/video/{conversionFormat}/" + galleryFile.getPublicPath());
        }
        galleryFileHolder.setContentType(galleryFile.getContentType());
        galleryFileHolder.setDateTaken(galleryFile.getDateTaken());
        return galleryFileHolder;
    }

    /**
     * Converts a list of the provided service layer {@link GalleryDirectory} files for web models {@link GalleryDirectoryHolder} objects.
     *
     * @param contextPath        Webapp context path.
     * @param galleryDirectories List of service layer directories.
     * @return A list of gallery directory holders
     */
    private List<GalleryDirectoryHolder> convertToGalleryDirectoryHolders(String contextPath, List<GalleryDirectory> galleryDirectories) {
        List<GalleryDirectoryHolder> galleryDirectoryHolders = new ArrayList<>(galleryDirectories.size());
        for (GalleryDirectory oneGalleryDirectory : galleryDirectories) {
            GalleryDirectoryHolder oneGalleryDirectoryHolder = new GalleryDirectoryHolder();
            String onePublicPath = oneGalleryDirectory.getPublicPath();
            oneGalleryDirectoryHolder.setName(oneGalleryDirectory.getName());
            oneGalleryDirectoryHolder.setPath(contextPath + SERVICE_PATH + onePublicPath);
            if (oneGalleryDirectory.getImage() != null) {
                oneGalleryDirectoryHolder.setImage(convertToGalleryFileHolder(contextPath, oneGalleryDirectory.getImage()));
            }
            galleryDirectoryHolders.add(oneGalleryDirectoryHolder);
        }
        return galleryDirectoryHolders;
    }

    /**
     * Generates the URL template for a certain image format.
     *
     * @param contextPath Webapp context path.
     * @param file        Image.
     * @return The URL for the image at the given image format code.
     */
    private String generateDynamicImageUrl(String contextPath, GalleryFile file) {
        return contextPath + "/image/{imageFormat}/" + file.getPublicPath();
    }

    /**
     * Generates the URL template for a certain image size.
     *
     * @param contextPath Webapp context path.
     * @param file        Image.
     * @return The URL template for the image at the given image format code. The URL will contain the placeholders {width} and {height}.
     * The idea with these is that a calling entitiy should swap those values for the actual width/height.
     */
    private String generateCustomImageUrlTemplate(String contextPath, GalleryFile file) {
        return contextPath + "/customImage/{width}/{height}/" + file.getPublicPath();
    }

    /**
     * Retrieves the image format for the image format code.
     *
     * @param code Code.
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