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
 *
 */
package com.github.henkexbg.gallery.service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.github.henkexbg.gallery.bean.UserInfo;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

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
     * Retrieves the public root paths for the current user. Any of these paths is
     * allowed to be accessed, though there is of course no guarantee the root path
     * will contain anything.
     *
     * @return A Map where the key is a descriptive name, and the value is the
     *         actual directory to which the user has access.
     */
    Map<String, File> getRootPathsForCurrentUser();

    /**
     * Looks up the actual file based on the public path. This method also checks
     * that the current user has right to access the file in question. It does
     * however NOT check whether the file actually exists - as long as the path is
     * allowed according to the configuration, this method will return the
     * corresponding file.
     *
     * @param publicPath Public path
     * @return The file or directory as pointed to by the public path for the
     *         current user
     * @throws NotAllowedException If the provided public path does not resolve to a
     *                             real file that the current user has access to
     * @throws IOException         If any more general errors occurs
     */
    File getRealFileOrDir(String publicPath) throws IOException, NotAllowedException;

    /**
     * Checks if current user is an admin user
     *
     * @return True if current user is admin user
     */
    boolean isAdmin();

    /**
     * Returns info about the current user
     * @return UserInfo
     */
    UserInfo getCurrentUserInfo();

    /**
     * The admin user is required for certain management tasks (for instance
     * cronjobs). This user should have all the rights of all the users. This
     * methods logs that user in. <br>
     * NOTE: It is probably a good idea never to call these methods from a thread
     * that also handles requests! Though it would be implementation dependent, it
     * does sound a bit risky no matter how you twist it. Just saying.
     */
    void loginAdminUser();

    /**
     * Logs out the admin user.
     */
    void logoutAdminUser();

}
