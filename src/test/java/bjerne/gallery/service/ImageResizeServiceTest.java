package bjerne.gallery.service;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bjerne.gallery.service.impl.ImageResizeServiceIMImpl;
import bjerne.gallery.service.impl.ImageResizeServiceImpl;

public class ImageResizeServiceTest {
    
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private String imageMagickPath = "C:/Program Files/ImageMagick-6.9.3-Q16";
    
    private String[] allowedSuffixes = new String[] { "JPG" };

    private File inputFilesDir = new File("C:/temp/testfiles");
    
    private File outputDir = new File("C:/temp/testfilesoutput/");
    
    private int outputResWidth = 1920;
    
    private int outputResHeight = 1080;

    @Test
    @Ignore
    public void testPerformanceNative() {
        ImageResizeService imageResizeServiceNative = new ImageResizeServiceImpl();
        testPerformance(imageResizeServiceNative);
    }
    
    @Test
    @Ignore
    public void testPerformanceIM() {
        ImageResizeService imageResizeServiceIM = new ImageResizeServiceIMImpl();
        ((ImageResizeServiceIMImpl) imageResizeServiceIM).setImageMagickPath(imageMagickPath);
        testPerformance(imageResizeServiceIM);
    }
    
    private void testPerformance(ImageResizeService imageResizeService) {
        long startTime = System.currentTimeMillis();
        Collection<File> fileCollection = (Collection<File>) FileUtils.listFiles(inputFilesDir, allowedSuffixes, false);
        fileCollection.parallelStream().forEach(f -> {wrapResizeNoException(imageResizeService, f, outputResWidth, outputResHeight);});
        long duration = System.currentTimeMillis() - startTime;
        LOG.info("DURATION: {}", duration);
    }
    
    private void wrapResizeNoException(ImageResizeService imageResizeService, File origImage, int width, int height) {
        try {
            imageResizeService.resizeImage(origImage, new File(outputDir.toString() + File.separator + origImage.getName()), width, height);
        } catch(IOException ioe) {
            fail();
        }
    }

    
    
}
