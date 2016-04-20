/**
 * Copyright (c) 2016 Henrik Bjerne
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:The above copyright
 * notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 */
package bjerne.gallery.service;

import java.io.IOException;
import java.util.List;

import bjerne.gallery.service.bean.GalleryFile;
import bjerne.gallery.service.exception.NotAllowedException;

/**
 * The core service providing the business logic for the application. The basic
 * structure is that this service maps between "public" paths and actual files
 * on the file system. Different users can have different public paths actually
 * pointing to the same actual file. The public path always starts with a root
 * dir as returned by {@link #getRootDirectories()}. Each root dir is publicly
 * just a name, whereas the implementation of this class will map that to actual
 * resources.
 * <p>
 * Each root dir can then contain additional directories, as well as files. In
 * the interest of this service, there are images and videos.
 * 
 * @author Henrik Bjerne
 *
 */
public interface GalleryService {

    /**
     * Retrieves all the files (not sub-directories) from the given public path.
     * 
     * @param publicPath
     *            Public path.
     * @return A list of <code>GalleryFile</code> objects.
     * @throws IOException
     *             If any issues retrieving the files.
     * @throws NotAllowedException
     *             If the requested path is not allowed.
     */
    List<GalleryFile> getDirectoryListingFiles(String publicPath) throws IOException, NotAllowedException;

    /**
     * Retrieves an image with the specified dimensions. The implementation
     * should make sure the image is properly rescaled. The service may also
     * define max and min image sizes.
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
    GalleryFile getImage(String publicPath, int width, int height) throws IOException, NotAllowedException;

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
    List<String> getDirectories(String publicPath) throws IOException, NotAllowedException;

    /**
     * Retrieves the root directories for the current user.
     * 
     * @return A list of root dir names.
     */
    List<String> getRootDirectories();

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
    GalleryFile getGalleryFile(String publicPath) throws IOException, NotAllowedException;

    /**
     * Returns a list of different video modes that are allowed to use. These
     * modes could for instance be different quality settings.
     * 
     * @return A list of video modes.
     */
    List<String> getAvailableVideoModes();

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
    GalleryFile getVideo(String publicPath, String videoMode) throws IOException, NotAllowedException;

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
    List<GalleryFile> getAllVideos() throws IOException, NotAllowedException;

}
