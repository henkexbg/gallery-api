package com.github.henkexbg.gallery.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@Configuration
public class DbTableSetup {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Resource
    private DataSource dataSource;

    @PostConstruct
    public void executeCreateTable() throws Exception {
        try(Connection connection = dataSource.getConnection()) {
            // Works but uses geometry
            String create = """
                    CREATE TABLE IF NOT EXISTS location (
                        pk INT NOT NULL,
                        the_geom GEOMETRY,
                        name VARCHAR(255),
                        country_name VARCHAR(255),
                        country_iso_a2 VARCHAR(5),
                        adm1name VARCHAR(255),
                        PRIMARY KEY (pk)
                    );
                    """;

            Statement st = connection.createStatement();
            st.execute(create);

            String createFileTable = """
                    CREATE TABLE IF NOT EXISTS gallery_file (
                        id IDENTITY PRIMARY KEY,
                        parent_id BIGINT,
                        path_on_disk VARCHAR(2048) UNIQUE NOT NULL,
                        is_directory BOOLEAN NOT NULL,
                        file_type ENUM('IMAGE', 'VIDEO'),
                        content_type VARCHAR(100),
                        location GEOMETRY,
                        nearest_location_id INT,
                        date_taken TIMESTAMP,
                        last_modified TIMESTAMP NOT NULL,
                        FOREIGN KEY (parent_id) REFERENCES gallery_file(id) ON DELETE CASCADE,
                        FOREIGN KEY (nearest_location_id) REFERENCES location(pk) ON DELETE CASCADE
                    );
                    """;

            st = connection.createStatement();
            st.execute(createFileTable);

            String createFilePartTable = """
                    CREATE TABLE IF NOT EXISTS filename_part (
                        file_id BIGINT NOT NULL,
                        part_index INT NOT NULL,
                        part VARCHAR(255) NOT NULL,
                        PRIMARY KEY (file_id, part_index),
                        FOREIGN KEY (file_id) REFERENCES gallery_file(id) ON DELETE CASCADE
                    );
                    """;

            st = connection.createStatement();
            st.execute(createFilePartTable);

            String createTagTable = """
                    CREATE TABLE IF NOT EXISTS tag (
                        id IDENTITY PRIMARY KEY,
                        file_id BIGINT,
                        text VARCHAR_IGNORECASE(255) NOT NULL,
                        source ENUM('FILENAME', 'LOCATION'),
                        FOREIGN KEY (file_id) REFERENCES gallery_file(id) ON DELETE CASCADE
                    );
                    """;
            st = connection.createStatement();
            st.execute(createTagTable);
        } catch (Exception e) {
            LOG.error("Error when setting up database tables!", e);
            throw e;
        }
    }

    private void loadData(Connection connection) {
        try {
            connection.setAutoCommit(false);
            Statement st = connection.createStatement();

            // Works but uses geometry
            String insertPrep = "INSERT INTO location (pk, the_geom, name, country_name, country_iso_a2, adm1name) VALUES (?, ST_GeomFromText(?, 4326), ?, ?, ?, ?)";

//            String insert = "INSERT INTO LOCATIONS (PK, THE_GEOM, NAME, COUNTRY_NAME, COUNTRY_ISO_A2) VALUES (%s, ST_GeomFromText('POINT(%s %s)', 4326), %s, %s, %s)";
            long startTime = System.currentTimeMillis();
            ResultSet resultSet = st.executeQuery("SELECT PK, NAME, ADM0NAME, ISO_A2, LATITUDE, LONGITUDE, ADM1NAME FROM POPULATED_PLACES");
            while (resultSet.next()) {
                Integer pk = resultSet.getInt(1);
                String name = resultSet.getString(2);
                String countryName = resultSet.getString(3);
                String countryIso = resultSet.getString(4);
                BigDecimal lat = resultSet.getBigDecimal(5);
                BigDecimal lon = resultSet.getBigDecimal(6);
                String adm1Name = resultSet.getString(7);
//                try {
                //Statement createSt = connection.createStatement();

                String point = "POINT(%s %s)".formatted(lon, lat);
                //String insertRow = insert.formatted(pk, lat, lon, name, countryName, countryIso);
                //createSt.execute(insertRow);

                PreparedStatement preparedStatement = connection.prepareStatement(insertPrep);
                preparedStatement.setInt(1, pk);
                preparedStatement.setString(2, point);
                preparedStatement.setString(3, name);
                preparedStatement.setString(4, countryName);
                preparedStatement.setString(5, countryIso);
                preparedStatement.setString(6, adm1Name);
                preparedStatement.execute();
//                } catch (Exception e) {
//                    LOG.error("Failed on pk %s".formatted(pk), e);
//                }
            }
            connection.commit();
            LOG.info("Time to load: {} ms",  System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (Exception e2) {
                LOG.error("Exception during rollback", e2);
            }
            LOG.error("Error when loading", e);
        }
    }
}
