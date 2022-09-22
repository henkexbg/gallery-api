package com.github.henkexbg.gallery.strategy.impl;

import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class FilenameToSearchTermsStrategyImpl implements FilenameToSearchTermsStrategy {

    @Override
    public Collection<String> generateSearchTermsFromFilename(String filename) {
        return Arrays.asList(filename.split("\\s+"));
    }
}
