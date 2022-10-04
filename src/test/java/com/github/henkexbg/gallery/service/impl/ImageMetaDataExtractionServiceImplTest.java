package com.github.henkexbg.gallery.service.impl;

import com.thebuzzmedia.exiftool.ExifTool;
import com.thebuzzmedia.exiftool.ExifToolBuilder;
import com.thebuzzmedia.exiftool.Tag;

import com.thebuzzmedia.exiftool.core.StandardTag;
import org.junit.Test;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ImageMetaDataExtractionServiceImplTest {

//    @Test
//    public void testBasic() throws Exception {
//        ImageInputStream imageInputStream = ImageIO.createImageInputStream(new File("/media/grejs/Bilder/2022-06-11 - 2022-06-13 Killcare/20220611_141447.jpg"));
//        Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
//
//        if (readers.hasNext()) {
//            // pick the first available ImageReader
//            final ImageReader reader = readers.next();
//            // attach source to the reader
//            reader.setInput(imageInputStream, true);
//            // read metadata of first image
//            final IIOMetadata metadata = reader.getImageMetadata(0);
//            final String[] names = metadata.getMetadataFormatNames();
//            final int length = names.length;
//            for (int i = 0; i < length; i++) {
//                Node asTree = metadata.getAsTree(names[i]);
//                String apa = "2";
//            }
//        }
//    }
    int MAXIMUM_TEXT_CHUNK_SIZE = 1000;
    @Test
    public void testBasic2() throws Exception {
//        Tika tika = new Tika();
//        Metadata metadata = new Metadata();
        InputStream inputstream = new FileInputStream("/media/grejs/Bilder/2022-06-11 - 2022-06-13 Killcare/20220611_141447.jpg");
//        tika.parse(inputstream, metadata);
//        String s = "asd";

//        //detecting the file type
//        BodyContentHandler handler = new BodyContentHandler();
//        Metadata metadata = new Metadata();
//        ParseContext pcontext = new ParseContext();
//
//        //Jpeg Parse
//        JpegParser jpegParser = new JpegParser();
//        JpegParser.parse(inputstream, handler, metadata,pcontext);
//        System.out.println("Contents of the document:" + handler.toString());
//        System.out.println("Metadata of the document:");
//        String[] metadataNames = metadata.names();
//
//        for(String name : metadataNames) {
//            System.out.println(name + ": " + metadata.get(name));
//        }



//        Parser parser = new AutoDetectParser();
//        ContentHandler handler = new BodyContentHandler();
//        Metadata metadata = new Metadata();
//        ParseContext context = new ParseContext();
//
//        parser.parse(inputstream, handler, metadata, context);
//        System.out.println(metadata.toString());

        ExifTool tool = new ExifToolBuilder().withPath("/usr/bin/exiftool").build();

        List<Tag> tags = List.of(StandardTag.CREATE_DATE, StandardTag.GPS_LATITUDE, StandardTag.GPS_LONGITUDE);

//        Map<Tag, String> imageMeta = tool.getImageMeta(  new File("/media/grejs/Bilder/2022-06-11 - 2022-06-13 Killcare/20220611_141447.jpg"), tags);
//        Map<Tag, String> imageMeta = tool.getImageMeta(new File("/media/grejs/Bilder/2017-06-16 Vivid/20170616_181145.jpg"), tags);

//        Map<Tag, String> imageMeta = tool.getImageMeta(new File("/media/grejs/Bilder/2022-06-11 - 2022-06-13 Killcare/20220612_095410.mp4"), tags);

        Map<Tag, String> imageMeta = tool.getImageMeta(new File("/home/henrik/Downloads/20220925_214915.jpg"));


        System.out.println(imageMeta);

//            final List<String> chunks = new ArrayList<>();
//            chunks.add("");
//            ContentHandlerDecorator handler = new ContentHandlerDecorator() {
//                @Override
//                public void characters(char[] ch, int start, int length) {
//                    String lastChunk = chunks.get(chunks.size() - 1);
//                    String thisStr = new String(ch, start, length);
//
//                    if (lastChunk.length() + length > MAXIMUM_TEXT_CHUNK_SIZE) {
//                        chunks.add(thisStr);
//                    } else {
//                        chunks.set(chunks.size() - 1, lastChunk + thisStr);
//                    }
//                }
//            };
//
//            AutoDetectParser parser = new AutoDetectParser();
//            Metadata metadata = new Metadata();
//            try (InputStream stream = TextContentHandler.class.get("/media/grejs/Bilder/2022-06-11 - 2022-06-13 Killcare/20220611_141447.jpg")) {
//                parser.parse(stream, handler, metadata);
////                return chunks;
//                System.out.println("Chunks" + chunks);
//            }
        }

        @Test
    public void testBasic3() throws IOException {
            MetadataExtractionServiceImpl metadataExtractionService = new MetadataExtractionServiceImpl();
            metadataExtractionService.init();
            //MetadataExtractionServiceImpl.FileMetaData metadata = metadataExtractionService.getMetadata(new File("/media/grejs/Bilder/2017-06-16 Vivid/20170616_181145.jpg"));
            MetadataExtractionServiceImpl.FileMetaData metadata = metadataExtractionService.getMetadata(new File("/home/henrik/Downloads/20220925_214915.jpg"));

            System.out.println("Result: " + metadata);

        }

}
