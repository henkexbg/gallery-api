package com.github.henkexbg.gallery.service;

import com.github.henkexbg.gallery.bean.GalleryFile;
import com.github.henkexbg.gallery.service.exception.NotAllowedException;

import java.io.IOException;
import java.util.List;

public interface GallerySearchService {

    List<GalleryFile> search(String publicPath, String searchTerm) throws IOException, NotAllowedException;
}
