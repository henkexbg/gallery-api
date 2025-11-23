/**
 * Copyright (c) 2021 Henrik Bjerne
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:The above copyright
 * notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.henkexbg.gallery;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.henkexbg.gallery.bean.DbFile;
import com.github.henkexbg.gallery.bean.Location;
import org.h2gis.functions.factory.H2GISFunctions;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.BeanMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.github.henkexbg.gallery.controller.model.ImageFormat;

import javax.sql.DataSource;

/**
 * Spring Boot starter class for the whole program. Also adds Spring web and
 * security configuration, as well as manages some more complicated
 * configuration from properties.
 *
 * @author Henrik
 */
@SpringBootApplication
@Configuration
@EnableWebSecurity
public class Application implements WebMvcConfigurer {

//    private String allowedCorsOrigins = null;

    @Value("${gallery.h2.connectionString}")
    private String h2ConnectionString;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Bean("objectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }

    /**
     * Returns a list of {@link ImageFormat} objects that are automatically
     * populated based on properties with the configured prefix.
     *
     * @return A list of ImageFormat objects
     */
    @Bean("imageFormats")
    @ConfigurationProperties(prefix = "gallery.image-formats")
    public List<ImageFormat> getImageFormats() {
        return new ArrayList<>();
    }

    /**
     * Returns a map of video conversion modes that is automatically populated based
     * on properties with the configured prefix.
     *
     * @return A Map where the key is the name of the conversion mode, and the value
     * is the executable pattern
     */
    @Bean("videoConversionModes")
    @ConfigurationProperties(prefix = "gallery.video.conversion-modes")
    public Map<String, String> getVideoConversionModes() {
        return new HashMap<>();
    }

    @Bean
    public DataSource h2DataSource() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(h2ConnectionString);
        Connection connection = dataSource.getConnection();
        H2GISFunctions.load(connection);
        return dataSource;
    }

    @Bean
    public Jdbi jdbi(DataSource dataSource) {
        Jdbi jdbi = Jdbi.create(dataSource);
        jdbi.registerRowMapper(BeanMapper.factory(Location.class));
        jdbi.registerRowMapper(BeanMapper.factory(DbFile.class));
        return jdbi;
    }

//    @Bean
//    public DataSource dataSource() {
//        return new EmbeddedDatabaseBuilder().
//                .setType(EmbeddedDatabaseType.H2)
//                .addScript("classpath:jdbc/schema.sql")
//                .addScript("classpath:jdbc/test-data.sql").build();
//    }

}