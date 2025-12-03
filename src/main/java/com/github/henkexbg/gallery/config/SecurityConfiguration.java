package com.github.henkexbg.gallery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Configuration
public class SecurityConfiguration {

    private Properties usersProperties;

    @Value("${gallery.web.allowedOrigins}")
    private String allowedOrigins;

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

    // TODO: Still testing out whether the standard Spring security config will work
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        //http.csrf(AbstractHttpConfigurer::disable).authorizeRequests().anyRequest().authenticated().and().httpBasic();
//        http.cors(cors -> corsConfigurationSource()).csrf(AbstractHttpConfigurer::disable).securityMatcher("/**").authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated()).httpBasic(Customizer.withDefaults());
//
//        return http.build();
//    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setAllowPrivateNetwork(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}