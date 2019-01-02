package com.github.henkexbg.gallery.strategy;

import java.util.Collection;

public interface FilenameToSearchTermsStrategy {

    Collection<String> generateSearchTermsFromFilename(String filename);

}
