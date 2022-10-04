package com.github.henkexbg.gallery.service.impl;

import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GallerySearchService;
import com.github.henkexbg.gallery.service.GalleryService;
import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;
import com.github.henkexbg.gallery.strategy.impl.FilenameToSearchTermsStrategyImpl;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.mockito.Matchers.any;

import java.io.File;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

//@ExtendWith(SpringExtension.class)
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(locations= "/applicationContext.xml")
@ContextConfiguration
//@WebAppConfiguration
@SpringBootTest
public class GallerySearchServiceImplTest {

//    GallerySearchServiceImpl gallerySearch;

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    GallerySearchService gallerySearchService;

//    @Mock
    @Autowired
    private GalleryAuthorizationService galleryAuthorizationService;

    @Autowired
    private GalleryService galleryService;

    @Autowired
    private FilenameToSearchTermsStrategy filenameToSearchTermsStrategy;
    //private FilenameToSearchTermsStrategy filenameToSearchTermsStrategy = new FilenameToSearchTermsStrategyImpl();

    private Map<String, File> rootPaths;


    @Before
    public void betweenTests() throws Exception {
//        gallerySearch = new GallerySearchServiceImpl();
//        MockitoAnnotations.initMocks(this);
//        gallerySearch.setGalleryAuthorizationService(galleryAuthorizationService);
//        gallerySearch.setGalleryService(galleryService);
//        gallerySearch.setFilenameToSearchTermsStrategy(filenameToSearchTermsStrategy);
//        gallerySearch.setDbHost("localhost");
//        gallerySearch.setDbPort(8529);
//        gallerySearch.setDbUsername("root");
//        gallerySearch.setDbPassword("Zvklje672!!");
//        gallerySearch.setDbName("gallery");
//
//        rootPaths = new HashMap<>();
//        rootPaths.put("testPath", new File("/home/henrik/dev/gallery-test-data/Bilder"));
//
//        when(galleryService.getRealFileOrDir("testPath")).thenReturn(new File("/home/henrik/dev/gallery-test-data/Bilder"));
//        when(galleryService.isAllowedExtension(any())).thenReturn(true);
//
//        //when(galleryService.getRootDirectories()).thenReturn(rootPaths);
//        when(galleryAuthorizationService.getRootPathsForCurrentUser()).thenReturn(rootPaths);
//        gallerySearch.init();

    }

//    @Test
//    @Ignore
//    public void testGetImage() throws Exception {
//        gallerySearch.searchTest();
//    }

    @Test
    public void testQuery() throws Exception {
        galleryAuthorizationService.loginAdminUser();
        List<GalleryFile> searchResult = gallerySearchService.search(null, "Australia");
        for (GalleryFile oneGalleryFile : searchResult) {
            LOG.info("One gallery file: {}", oneGalleryFile);
        }
    }

    @Test
    @Ignore
    public void testUpdateDirs() throws Exception {
        ((GallerySearchServiceImpl) gallerySearchService).createOrUpdateAllDirectories();
    }

    @Configuration
    @ComponentScan("com.github.henkexbg")
    public static class SpringConfig {

    }
}
