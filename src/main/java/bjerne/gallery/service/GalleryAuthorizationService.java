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

import java.io.File;
import java.util.Map;

/**
 * Service for authorization-related functionality, mostly around determining
 * which files a certain user has access to. <br>
 * A number of root paths are defined per user. This service assumes that all
 * files under this root paths are considered allowed.
 * 
 * @author Henrik Bjerne
 *
 */
public interface GalleryAuthorizationService {

    /**
     * Retrieves the public root paths for the current user. Any of these paths
     * is allowed to be accessed, though there is of course no guarantee the
     * root path will contain anything.
     * 
     * @return A Map where the key is a descriptive name, and the value is the
     *         actual directory to which the user has access.
     */
    Map<String, File> getRootPathsForCurrentUser();

    /**
     * Verifies whether a certain file is allowed to be accessed by the current
     * user. Essentially this method should return true if the file is a child
     * (direct or indirect) or any of the root paths returned by
     * {@link #getRootPathsForCurrentUser()}
     * 
     * @param fileToCheck
     *            File to check
     * @return True if current user is allowed to access file.
     */
    boolean isAllowed(File fileToCheck);

    /**
     * The admin user is required for certain management tasks (for instance
     * cronjobs). This user should have all the rights of all the users. This
     * methods logs that user in. <br>
     * NOTE: It is probably a good idea never to call these methods from a
     * thread that also handles requests! Though it would be implementation
     * dependent, it does sound a bit risky no matter how you twist it. Just
     * saying.
     */
    void loginAdminUser();

    /**
     * Logs out the admin user.
     */
    void logoutAdminUser();

}
