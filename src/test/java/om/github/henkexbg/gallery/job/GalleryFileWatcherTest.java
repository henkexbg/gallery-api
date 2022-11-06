package om.github.henkexbg.gallery.job;

import com.github.henkexbg.gallery.job.FileChangeListener;
import com.github.henkexbg.gallery.job.GalleryFileWatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@SpringBootTest
public class GalleryFileWatcherTest {

    private final Logger LOG = LoggerFactory.getLogger(getClass());
    @Autowired
    private GalleryFileWatcher galleryFileWatcher;

    @Test
    public void testSimple() throws Exception {
        final Set<File> allCreatedOrUpdatedFiles = new HashSet<>();
        final Set<File> allDeletedFiles = new HashSet<>();
        FileChangeListener fcl = new FileChangeListener() {
            @Override
            public void fireUpdateFiles(Set<File> createdOrUpdatedFiles, Set<File> deletedFiles) {
                allCreatedOrUpdatedFiles.addAll(createdOrUpdatedFiles);
                allDeletedFiles.addAll(deletedFiles);
                LOG.info("Found {} created/updated files: {}", allCreatedOrUpdatedFiles.size(), allCreatedOrUpdatedFiles);
                LOG.info("Found {} deleted files: {}", allDeletedFiles.size(), allDeletedFiles);
            }
        };
        galleryFileWatcher.setFileChangeListeners(List.of(fcl));
        //Thread.sleep(50000);
        System.in.read();
        LOG.info("Found {} created/updated files: {}", allCreatedOrUpdatedFiles.size(), allCreatedOrUpdatedFiles);
        LOG.info("Found {} deleted files: {}", allDeletedFiles.size(), allDeletedFiles);
    }

    @Configuration
    @ComponentScan("com.github.henkexbg")
    public static class SpringConfig {
    }

    }
