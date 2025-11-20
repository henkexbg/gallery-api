package com.github.henkexbg.gallery.strategy.impl;

import com.github.henkexbg.gallery.strategy.FilenameToSearchTermsStrategy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Component("filenameToSearchTermsStrategy")
public class FilenameToSearchTermsStrategyImpl implements FilenameToSearchTermsStrategy {

    @Override
    public List<String> generateSearchTermsFromFilename(File file) {
        String pureFileName = file.isDirectory() ? file.getName() : file.getName().substring(0, file.getName().lastIndexOf('.'));
        String allLowerCaseFilename = pureFileName.toLowerCase();
        return Arrays.asList(allLowerCaseFilename.split("\\W"));
    }
}
