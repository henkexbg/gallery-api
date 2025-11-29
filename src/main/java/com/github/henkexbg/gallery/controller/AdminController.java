package com.github.henkexbg.gallery.controller;

import com.github.henkexbg.gallery.service.GalleryAuthorizationService;
import com.github.henkexbg.gallery.service.GallerySearchService;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    private GalleryAuthorizationService galleryAuthorizationService;

    @Resource
    private GallerySearchService gallerySearchService;

    @PostMapping("/db/full")
    public void updateDatabase() throws Exception {
        if (!galleryAuthorizationService.isAdmin()) {
            throw new NotAllowedException("Not allowed");
        }
        LOG.info("Performing full DB refresh");
        try {
            gallerySearchService.createOrUpdateAllDirectories();
        } catch (Exception e) {
            LOG.error("Exception while performing full DB refresh", e);
        }
    }

}
