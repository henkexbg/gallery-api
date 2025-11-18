package com.github.henkexbg.gallery.strategy;

import java.io.File;
import java.util.List;

public interface FilenameToSearchTermsStrategy {

    List<String> generateSearchTermsFromFilename(File file);

}
