package com.github.henkexbg.gallery.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

/**
 * The tests in this class are ignored per default. They work, but require
 * instance-specific configuration and can not be enabled per default
 * 
 * @author Henrik
 *
 */
public class VideoConversionServiceTest {

    @Test
    @Ignore
    public void testVideoConversion() throws Exception {
        Map<String, String> conversionModes = new HashMap<>();
        conversionModes.put("COMPACT", "%s,-v,quiet,-i,%s,%s");

        VideoConversionService videoConversionService = new VideoConversionService();
        videoConversionService.externalBinaryPath = "C:/Program Files/ffmpeg-20180202-caaa40d-win64-static/bin/ffmpeg.exe";
        videoConversionService.videoConversionModes = conversionModes;
        File inputFile = new File("C:/temp/MVI_0647.MP4");
        File outputFile = new File("C:/temp/video.mp4");
        videoConversionService.convertVideo(inputFile);
    }

    @Test
    @Ignore
    public void generateImageForVideo() throws Exception {
        String imageCommandTemplate = "%1$s,-i,%2$s,-ss,00:00:00.500,-filter:v,scale=\"%4$s:%5$s\",-vframes,1,%3$s";

        VideoConversionService videoConversionService = new VideoConversionService();
        videoConversionService.externalBinaryPath = "C:/Program Files/ffmpeg-20180202-caaa40d-win64-static/bin/ffmpeg.exe";
        videoConversionService.imageCommandTemplate = imageCommandTemplate;
        File inputFile = new File("C:/temp/MVI_0647.MP4");
        File outputFile = new File("C:/temp/image.jpg");
        videoConversionService.generateImageForVideo(inputFile, outputFile, 1024, 768);
    }
    
}
