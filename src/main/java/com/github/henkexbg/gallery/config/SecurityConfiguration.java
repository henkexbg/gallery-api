package com.github.henkexbg.gallery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@Configuration
public class SecurityConfiguration {

    private Properties usersProperties;

    @Value("${gallery.users.propertiesFile}")
    public void setUsersPropertiesFile(String usersPropertiesFile) {
        try {
            usersProperties = new Properties();
            usersProperties.load(new FileInputStream(usersPropertiesFile));
        } catch (IOException ioe) {
            throw new IllegalArgumentException("No valid user properties file given!");
        }
    }
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(usersProperties);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable().authorizeRequests().anyRequest().authenticated().and().httpBasic();
        http.cors();
        return http.build();
    }

}