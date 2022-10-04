package com.github.henkexbg.gallery.strategy;

import java.io.File;
import java.util.Collection;

public interface FilenameToSearchTermsStrategy {

    Collection<String> generateSearchTermsFromFilename(File file);

}
