package bjerne.gallery.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import bjerne.gallery.service.impl.VideoConversionServiceImpl;

public class VideoConversionServiceTest {

    @Test
    @Ignore
    public void testVideoConversion() throws Exception {
        Map<String, String> conversionModes = new HashMap<>();
        conversionModes.put("COMPACT", "%s -v quiet -i %s %s");
        //
        VideoConversionServiceImpl videoConversionService = new VideoConversionServiceImpl(); 
        videoConversionService.setConversionModes(conversionModes);
        File inputFile = new File("C:/temp/bilder/MVI_2746.MP4");
        File outputFile = new File("C:/temp/hejsan/hejsan2/MVI_2746_out.MP4"); 
        videoConversionService.convertVideo(inputFile, outputFile, videoConversionService.getAvailableVideoModes().iterator().next());
    }
    
}
