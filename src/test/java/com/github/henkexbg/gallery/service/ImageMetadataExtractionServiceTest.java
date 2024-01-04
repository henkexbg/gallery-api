package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.service.impl.MetadataExtractionServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@SpringBootTest
public class ImageMetadataExtractionServiceTest {

    @Autowired
    MetadataExtractionService metadataExtractionService;

    @Test
    public void testBasic3() throws IOException {
//        MetadataExtractionServiceImpl metadataExtractionService = new MetadataExtractionServiceImpl();
//        metadataExtractionService.init();
        MetadataExtractionServiceImpl.FileMetaData metadata = metadataExtractionService.getMetadata(new File("/media/grejs/Bilder/2023-05-06 - 2023-05-07 Camping The Basin/20230506_132834.jpg"));
        System.out.println("Result: " + metadata);

    }

    @Configuration
    @ComponentScan("com.github.henkexbg")
    public static class SpringConfig {

    }
}
