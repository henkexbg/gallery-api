package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.bean.LocationMetadata;
import com.github.henkexbg.gallery.bean.LocationResult;
import com.github.henkexbg.gallery.service.impl.MetadataExtractionServiceImpl;
import org.junit.Ignore;
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
public class LocationMetadataServiceTest {

    @Autowired
    LocationMetadataService locationMetadataService;

    @Test
    @Ignore
    public void testCreation() throws IOException {
        locationMetadataService.createAndLoadCollection();
    }

    @Test
    public void testRetrieval() throws IOException {
        LocationResult locationMetadata = locationMetadataService.getLocationMetadata(-33.7770963, 151.2918732);
        String apa = "1";
    }

    @Configuration
    @ComponentScan("com.github.henkexbg")
    public static class SpringConfig {

    }
}
