package com.github.henkexbg.gallery.strategy.impl;

import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class FilenameToSearchTermsStrategyImpl implements FilenameToSearchTermsStrategy {

    @Override
    public Collection<String> generateSearchTermsFromFilename(File file) {
        String pureFileName = file.isDirectory() ? file.getName() : file.getName().substring(0, file.getName().lastIndexOf('.'));
        String allLowerCaseFilename = pureFileName.toLowerCase();
        return Arrays.asList(allLowerCaseFilename.split("\\W"));
    }
}
