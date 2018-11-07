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
package com.github.henkexbg.gallery.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryRootDirChangeListener;
import com.github.henkexbg.gallery.bean.GalleryRootDir;

/**
 * Implementation of the {@link GalleryAuthorizationService} using Spring
 * Security framework. The roles of the current user will be evaluated towards
 * the roles of each root dir to see whether a user has the right to access that
 * root dir or not. This class also listens to
 * {@link GalleryRootDirChangeListener} in order to be able to get runtime
 * changes of the root dirs.
 * 
 * @author Henrik Bjerne
 *
 */
public class GalleryAuthorizationServiceSSImpl implements GalleryAuthorizationService, GalleryRootDirChangeListener {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private Collection<GalleryRootDir> rootDirs;

    @Override
    public boolean isAllowed(File fileToCheck) {
        if (rootDirs == null || rootDirs.isEmpty()) {
            return false;
        }
        try {
            if (fileToCheck == null || StringUtils.isBlank(fileToCheck.getCanonicalPath())) {
                return false;
            }
            String pathToCheck = fileToCheck.getCanonicalPath();
            Collection<String> currentUserRoles = getCurrentUserRoles();
            boolean allowed = isAllowed(currentUserRoles, rootDirs, pathToCheck);
            LOG.debug("Is path {} allowed: {}", pathToCheck, allowed);
            return allowed;
        } catch (IOException ioe) {
            return false;
        }
    }

    @Override
    public void setRootDirs(Collection<GalleryRootDir> rootDirs) {
        LOG.debug("Updating rootDirs");
        this.rootDirs = rootDirs;
    }

    @Override
    public Map<String, File> getRootPathsForCurrentUser() {
        Collection<String> currentUserRoles = getCurrentUserRoles();
        Map<String, File> rootPaths = getRootPathsForRoles(currentUserRoles, rootDirs);
        LOG.debug("Root paths: {}", rootPaths);
        return rootPaths;
    }

    private Map<String, File> getRootPathsForRoles(Collection<String> roles, Collection<GalleryRootDir> rootDirs) {
        Map<String, File> rootPathsForRoles = rootDirs.stream().filter(r -> roles.contains(r.getRole()))
                .collect(Collectors.toMap(GalleryRootDir::getName, GalleryRootDir::getDir));
        LOG.debug("Root paths for roles {}: {}", roles, rootPathsForRoles);
        return rootPathsForRoles;
    }

    private boolean isAllowed(Collection<String> roles, Collection<GalleryRootDir> rootDirs, String pathToCheck) {
        Map<String, File> rootPathsForRoles = getRootPathsForRoles(roles, rootDirs);
        return rootPathsForRoles.values().stream().anyMatch(f -> stringStartsWithFile(pathToCheck, f));
    }

    private boolean stringStartsWithFile(String string, File f) {
        try {
            return string.startsWith(f.getCanonicalPath());
        } catch (IOException ioe) {
            return false;
        }
    }

    private Collection<String> getCurrentUserRoles() {
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        Collection<String> currentUserRoles = authorities.stream().map(e -> e.getAuthority()).collect(Collectors.toSet());
        LOG.debug("Roles for current user: {}", currentUserRoles);
        return currentUserRoles;
    }

    @Override
    public void loginAdminUser() {
        Authentication auth = new Authentication() {

            private static final long serialVersionUID = -7444637463199474476L;

            @Override
            public String getName() {
                return "admin";
            }

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return rootDirs.stream().map(rd -> new SimpleGrantedAuthority(rd.getRole())).collect(Collectors.toList());
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "admin";
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean arg0) throws IllegalArgumentException {
            }

        };
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Override
    public void logoutAdminUser() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Anonymous class for returning a string as an authority.
     * 
     * @author Henrik Bjerne
     *
     */
    private class SimpleGrantedAuthority implements GrantedAuthority {

        private static final long serialVersionUID = -3092055743053594017L;

        public SimpleGrantedAuthority(String role) {
            this.role = role;
        }

        private String role;

        @Override
        public String getAuthority() {
            return role;
        }

    }

}
