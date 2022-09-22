package com.github.henkexbg.gallery.service.impl;

import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;
import com.github.henkexbg.gallery.strategy.impl.FilenameToSearchTermsStrategyImpl;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.any;

import java.io.File;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@Ignore
public class GallerySearchServiceImplTest {

    GallerySearchServiceImpl gallerySearch;

    @Mock
    private GalleryAuthorizationService galleryAuthorizationService;

    @Mock
    private GalleryService galleryService;

    private FilenameToSearchTermsStrategy filenameToSearchTermsStrategy = new FilenameToSearchTermsStrategyImpl();

    private Map<String, File> rootPaths;


    @Before
    public void betweenTests() throws Exception {
        gallerySearch = new GallerySearchServiceImpl();
        MockitoAnnotations.initMocks(this);
        gallerySearch.setGalleryAuthorizationService(galleryAuthorizationService);
        gallerySearch.setGalleryService(galleryService);
        gallerySearch.setFilenameToSearchTermsStrategy(filenameToSearchTermsStrategy);

        rootPaths = new HashMap<>();
        rootPaths.put("testPath", new File("C:/dev/gallery-test-data/Bilder"));

        when(galleryService.getRealFileOrDir("testPath")).thenReturn(new File("C:/dev/gallery-test-data/Bilder"));
        when(galleryService.isAllowedExtension(any())).thenReturn(true);

        //when(galleryService.getRootDirectories()).thenReturn(rootPaths);
        when(galleryAuthorizationService.getRootPathsForCurrentUser()).thenReturn(rootPaths);
        gallerySearch.init();

    }

    @Test
    public void testGetImage() throws Exception {
        gallerySearch.searchTest();
    }

    @Test
    public void testQuery() throws Exception {
        List<GalleryFile> searchResult = gallerySearch.search("testPath", "Michael");
        for (GalleryFile oneGalleryFile : searchResult) {
            System.out.println("One gallery file: " + oneGalleryFile);
        }
    }

    @Test
    public void testUpdateDirs() throws Exception {
        gallerySearch.createOrUpdateAllDirectories();
    }
}
