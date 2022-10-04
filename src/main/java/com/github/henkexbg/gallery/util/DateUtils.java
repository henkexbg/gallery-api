package com.github.henkexbg.gallery.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class DateUtils {

    private static final String CREATE_DATE_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss";

    private final DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .appendPattern(CREATE_DATE_DATE_FORMAT)
            .toFormatter()
            .withZone(ZoneId.of("UTC"));

//    public static Instant toInstant()

}
