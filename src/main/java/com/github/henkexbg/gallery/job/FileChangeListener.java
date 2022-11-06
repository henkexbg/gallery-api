package com.github.henkexbg.gallery.job;

import java.io.File;
import java.util.List;
import java.util.Set;

public interface FileChangeListener {

    void fireUpdateFiles(Set<File> createdOrUpdatedFiles, Set<File> deletedFiles);

}
