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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.github.henkexbg.gallery.bean.GalleryRootDir;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

/**
 * Tests the authorization service. The Spring application context is loaded to
 * get Spring Security to initialize.
 * 
 * @author Henrik
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class GalleryAuthorizationServiceSSImplTest {

	private GalleryAuthorizationServiceSSImpl galleryAuthorizationServiceSSImpl = new GalleryAuthorizationServiceSSImpl();

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testOneRootPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		Map<String, File> rootPathsForCurrentUser = galleryAuthorizationServiceSSImpl.getRootPathsForCurrentUser();

		assertEquals(rootPathsForCurrentUser.size(), 1, "Response did not contain exactly one root path");
		assertEquals(rootPathsForCurrentUser.get(grd1.getName()), grd1.getDir(),
				"Response did not contain the right root path");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testTwoRootPaths() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		GalleryRootDir grd2 = new GalleryRootDir();
		grd2.setDir(new File("/test/test2"));
		grd2.setName("test2-rd");
		grd2.setRole("ROLE_TEST");
		grds.add(grd2);

		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		Map<String, File> rootPathsForCurrentUser = galleryAuthorizationServiceSSImpl.getRootPathsForCurrentUser();

		assertEquals(rootPathsForCurrentUser.size(), 2, "Response did not contain two root paths");
		assertEquals(rootPathsForCurrentUser.get(grd1.getName()), grd1.getDir(),
				"Response did not contain the right root path");
		assertEquals(rootPathsForCurrentUser.get(grd2.getName()), grd2.getDir(),
				"Response did not contain the right root path");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testOneValidOneNonValidRootPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		GalleryRootDir grd2 = new GalleryRootDir();
		grd2.setDir(new File("/test/test2"));
		grd2.setName("test2-rd");
		grd2.setRole("ROLE_NOT_VALID");
		grds.add(grd2);

		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		Map<String, File> rootPathsForCurrentUser = galleryAuthorizationServiceSSImpl.getRootPathsForCurrentUser();

		assertEquals(rootPathsForCurrentUser.size(), 1, "Response did not contain exactly one root path");
		assertEquals(rootPathsForCurrentUser.get(grd1.getName()), grd1.getDir(),
				"Response did not contain the right root path");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testTwoValidRootPathsMerge() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		GalleryRootDir grd2 = new GalleryRootDir();
		// Same dir
		grd2.setDir(new File("/test/test1"));
		// Same name
		grd2.setName("test1-rd");
		grd2.setRole("ROLE_USER");
		grds.add(grd2);

		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		Map<String, File> rootPathsForCurrentUser = galleryAuthorizationServiceSSImpl.getRootPathsForCurrentUser();

		assertEquals(rootPathsForCurrentUser.size(), 1, "Response did not contain exactly one root path");
		assertEquals(rootPathsForCurrentUser.get(grd1.getName()), grd1.getDir(),
				"Response did not contain the right root path");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testNonValidRootPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		Map<String, File> rootPathsForCurrentUser = galleryAuthorizationServiceSSImpl.getRootPathsForCurrentUser();

		assertEquals(rootPathsForCurrentUser.size(), 0, "Response did not contain zero root paths");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirSimpleRootPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		File realFileOrDir = galleryAuthorizationServiceSSImpl.getRealFileOrDir(grd1.getName());
		assertEquals(grd1.getDir(), realFileOrDir, "Correct file not returned");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirConflictingRootPaths() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();

		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);

		GalleryRootDir grd2 = new GalleryRootDir();
		grd2.setDir(new File("/test/test2"));
		grd2.setName("test1-rd");
		grd2.setRole("ROLE_USER");
		grds.add(grd2);

		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		File realFileOrDir = galleryAuthorizationServiceSSImpl.getRealFileOrDir(grd1.getName());
		assertEquals(grd1.getDir(), realFileOrDir, "Correct file not returned");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirSimpleRootPathTrailingSlash() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		File realFileOrDir = galleryAuthorizationServiceSSImpl.getRealFileOrDir(grd1.getName() + "/");
		assertEquals(grd1.getDir().getCanonicalPath() + File.separatorChar, realFileOrDir.getCanonicalPath(),
				"Correct file not returned");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirNotAllowedSimpleRootPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		try {
			galleryAuthorizationServiceSSImpl.getRealFileOrDir("does-not-exist");
			fail("NotAllowedException should have been thrown");
		} catch (NotAllowedException fnfe) {
		}
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirLongerPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		String relativePath = "/another-dir/image.jpeg";
		File realFileOrDir = galleryAuthorizationServiceSSImpl.getRealFileOrDir(grd1.getName() + relativePath);
		assertEquals(new File(grd1.getDir(), relativePath), realFileOrDir, "Correct file not returned");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirNonCanonicalPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		String relativePath = "/non-valid/../test1/another-dir/image.jpeg";
		File realFileOrDir = galleryAuthorizationServiceSSImpl.getRealFileOrDir(grd1.getName() + relativePath);
		assertEquals(new File(grd1.getDir(), relativePath), realFileOrDir, "Correct file not returned");
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testGetRealFileOrDirNotAllowedNonCanonicalPath() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_TEST");
		grds.add(grd1);
		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		String relativePath = "/../non-valid/another-dir/image.jpeg";
		try {
			galleryAuthorizationServiceSSImpl.getRealFileOrDir(grd1.getName() + relativePath);
			fail("NotAllowedException should have been thrown");
		} catch (NotAllowedException nae) {
		}
	}

	@Test
	@WithMockUser(username = "test", roles = { "USER", "TEST" })
	public void testLoginAdminUser() throws Exception {
		List<GalleryRootDir> grds = new ArrayList<>();
		GalleryRootDir grd1 = new GalleryRootDir();
		grd1.setDir(new File("/test/test1"));
		grd1.setName("test1-rd");
		grd1.setRole("ROLE_NEW_ROLE1");
		grds.add(grd1);
		GalleryRootDir grd2 = new GalleryRootDir();
		grd2.setDir(new File("/test/test2"));
		grd2.setName("test2-rd");
		grd2.setRole("ROLE_NEW_ROLE2");
		grds.add(grd2);

		galleryAuthorizationServiceSSImpl.setRootDirs(grds);
		galleryAuthorizationServiceSSImpl.loginAdminUser();

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication()
				.getAuthorities();
		assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals(grd1.getRole())));
		assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals(grd2.getRole())));
	}
}
