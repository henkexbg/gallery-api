package com.github.henkexbg.gallery.bean;

import java.util.List;

public record SearchResult(List<GalleryDirectory> directories, List<GalleryFile> files) {
}
