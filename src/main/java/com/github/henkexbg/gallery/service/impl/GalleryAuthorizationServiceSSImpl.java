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
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.github.henkexbg.gallery.bean.GalleryRootDir;
import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.job.GalleryRootDirChangeListener;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

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

	/**
	 * Contains a map where each key is a role, and each value is a map where the
	 * key is the root path name and the value is the File of that root path
	 */
	Map<String, Map<String, File>> rootPathsPerRoleMap = new HashMap<>();

	@Override
	public File getRealFileOrDir(String publicPath) throws IOException, NotAllowedException {
		LOG.debug("Entering getRealFileOrDir(publicPath={})", publicPath);
		if (StringUtils.isBlank(publicPath)) {
			throw new NotAllowedException("Could not extract code from empty path!");
		}
		if (rootPathsPerRoleMap == null || rootPathsPerRoleMap.isEmpty()) {
			throw new NotAllowedException("Public path " + publicPath + " not allowed!");
		}
		int relativePathStartIndex = publicPath.indexOf("/");
		String baseDirCode = (relativePathStartIndex < 0) ? publicPath
				: publicPath.substring(0, relativePathStartIndex);
		LOG.debug("baseDirCode: {}", baseDirCode);

		Collection<String> currentUserRoles = getCurrentUserRoles();

		File baseDir = null;
		for (String oneRole : currentUserRoles) {
			Map<String, File> rootPathsOneRoleMap = rootPathsPerRoleMap.get(oneRole);
			if (rootPathsOneRoleMap != null) {
				baseDir = rootPathsOneRoleMap.get(baseDirCode);
			}
			if (baseDir != null) {
				break;
			}
		}
		if (baseDir == null) {
			String errorMessage = String.format("Could not find basedir for base dir code {}", baseDirCode);
			LOG.error(errorMessage);
			throw new NotAllowedException(errorMessage);
		}
		File file = null;
		if (relativePathStartIndex >= 0) {
			String relativePath = publicPath.substring(relativePathStartIndex, publicPath.length());
			LOG.debug("Relative path: {}", relativePath);
			file = new File(baseDir, relativePath);
			if (!isCanonicalChild(baseDir, file)) {
				throw new NotAllowedException("File " + file + " not allowed!");
			}

		} else {
			// No relative path - just use baseDir itself
			file = baseDir;
		}
		return file;
	}

	/**
	 * Internally converts the root dirs to a more efficient lookup structure and
	 * stores it in {@link #rootPathsPerRoleMap}
	 */
	@Override
	public void onGalleryRootDirsUpdated(Collection<GalleryRootDir> galleryRootDirs) {
		LOG.debug("Updating rootDirs");
		Collection<String> allRoles = galleryRootDirs.stream().map(r -> r.getRole()).collect(Collectors.toSet());
		Map<String, Map<String, File>> rootPathsPerRoleMap = new HashMap<>();
		for (String oneRole : allRoles) {
			Map<String, File> rootPathsForRoles = galleryRootDirs.stream().filter(rd -> oneRole.equals(rd.getRole()))
					.collect(Collectors.toMap(GalleryRootDir::getName, GalleryRootDir::getDir, (dir1, dir2) -> dir1));
			rootPathsPerRoleMap.put(oneRole, rootPathsForRoles);
		}
		this.rootPathsPerRoleMap = rootPathsPerRoleMap;
	}

	@Override
	public Map<String, File> getRootPathsForCurrentUser() {
		Collection<String> currentUserRoles = getCurrentUserRoles();
		Map<String, File> rootPathsForCurrentUser = new HashMap<>();
		rootPathsPerRoleMap.forEach((role, rps) -> {
			if (currentUserRoles.contains(role)) {
				rootPathsForCurrentUser.putAll(rps);
			}
		});
		return rootPathsForCurrentUser;
	}

	/**
	 * Simpler helper to validate that the child is indeed a canonical child of the
	 * parent file.
	 * 
	 * @param parent Supposed parent file
	 * @param child  Supposed child file
	 * @return True if child is a proper canonical child of parent
	 */
	private boolean isCanonicalChild(File parent, File child) {
		try {
			return child.getCanonicalPath().startsWith(parent.getCanonicalPath());
		} catch (IOException ioe) {
			return false;
		}
	}

	private Collection<String> getCurrentUserRoles() {
		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication()
				.getAuthorities();
		Collection<String> currentUserRoles = authorities.stream().map(e -> e.getAuthority())
				.collect(Collectors.toSet());
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
				return rootPathsPerRoleMap.keySet().stream().map(role -> new SimpleGrantedAuthority(role))
						.collect(Collectors.toList());
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

}
