package com.github.henkexbg.gallery.controller;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GallerySearchService;
import com.github.henkexbg.gallery.service.LocationLoader;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    private GalleryAuthorizationService galleryAuthorizationService;

    @Resource
    private GallerySearchService gallerySearchService;

    @Resource
    private LocationLoader locationLoader;

    @PostMapping("/db/full")
    public void updateDatabase(@RequestParam(required = false, defaultValue = "false") Boolean removeAll) throws Exception {
        if (!galleryAuthorizationService.isAdmin()) {
            throw new NotAllowedException("Not allowed");
        }
        LOG.info("Performing full DB refresh");
        try {
            gallerySearchService.createOrUpdateAllDirectories(removeAll);
        } catch (Exception e) {
            LOG.error("Exception while performing full DB refresh", e);
        }
    }

    @PostMapping("/db/locations")
    public void updateLocationsFromDefaultSource(@RequestParam(required = false) URI fileUri) throws Exception {
        if (!galleryAuthorizationService.isAdmin()) {
            throw new NotAllowedException("Not allowed");
        }
        LOG.info("Loading locations");
        try {
            locationLoader.loadDataFromGeonames(fileUri);
        } catch (Exception e) {
            LOG.error("Exception while performing full DB refresh", e);
            throw e;
        }
    }

}
