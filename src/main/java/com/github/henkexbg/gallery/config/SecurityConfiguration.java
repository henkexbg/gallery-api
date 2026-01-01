package com.github.henkexbg.gallery.config;

import com.github.henkexbg.gallery.security.CustomAuthenticationEntryPoint;
import com.github.henkexbg.gallery.security.CustomCsrfSecurityFilter;
import com.github.henkexbg.gallery.security.CustomSuccessHandler;
import jakarta.annotation.Priority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * The overarching security config has the following setup:
 * <ul>
 *     <li>Basic auth OR session-based authentication allowed</li>
 *     <li>Session-based auth uses standard form-based /login and /logout endpoints with empty response bodies</li>
 *     <li>CORS and CSRF use a simplified config based on whitelisted hosts. All origin ports are allowed, CSRF token is not required</li>
 * </ul>
 */
@Configuration
public class SecurityConfiguration {

    @Value("#{'${gallery.web.crossOrigin.allowedHosts}'.split(',')}")
    Set<String> allowedHosts;

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
    public CustomCsrfSecurityFilter customCsrfSecurityFilter() {
        return new CustomCsrfSecurityFilter();
    }

    @Bean
    public CustomSuccessHandler customSuccessHandler() {
        return new CustomSuccessHandler();
    }

    @Bean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new CustomAuthenticationEntryPoint();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(usersProperties);
    }

    /**
     * Filter chain for all service methods. Both basic auth and session based auth are allowed.
     *
     * @param http HttpSecurity
     * @return A configured security filter chain
     * @throws Exception If any error occurs
     */
    @Bean
    @Priority(3)
    public SecurityFilterChain serviceFilterChain(HttpSecurity http) throws Exception {
        return commonSecuredFilterChain(http, "/service/**", "/image/**", "/customImage/**", "/video/**").httpBasic(
                Customizer.withDefaults()).build();
    }

    /**
     * Filter chain for all session-based user methods, including login and logout. Basic auth is not allowed.
     *
     * @param http HttpSecurity
     * @return A configured security filter chain
     * @throws Exception If any error occurs
     */
    @Bean
    @Priority(5)
    public SecurityFilterChain sessionUserFilterChain(HttpSecurity http) throws Exception {
        return commonSecuredFilterChain(http, "/user", "/login", "/logout").formLogin(
                formLogin -> formLogin.loginPage("/login").permitAll().successHandler(customSuccessHandler())).build();
    }

    /**
     * Public files require no authentication. This is the catch-all.
     *
     * @param http HttpSecurity
     * @return A configured security filter chain
     * @throws Exception If any error occurs
     */
    @Bean
    @Priority(10)
    public SecurityFilterChain publicFilesFilterChain(HttpSecurity http) throws Exception {
        http.cors(_ -> corsConfigurationSource()).csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(customCsrfSecurityFilter(), CsrfFilter.class).securityMatcher("/**")
                .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Configures CORS. The main twist is that {@link #allowedHosts} is used to configure allowedOriginPatterns. For the given hosts, all
     * origins https requests for all ports are allowed. For localhost, http is also allowed.
     *
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        if (allowedHosts != null && !allowedHosts.isEmpty()) {
            List<String> allowedOriginPatterns = new ArrayList<>();
            allowedHosts.forEach(allowedHost -> {
                allowedOriginPatterns.add("https://%s:[*]".formatted(allowedHost));
                if (allowedHost.equals("localhost")) {
                    allowedOriginPatterns.add("http://%s:[*]".formatted(allowedHost));
                }
            });
            config.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setAllowPrivateNetwork(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Common configuration for all secured endpoints.
     *
     * @param http            HttpSecurity
     * @param requestMatchers List of request matchers (patterns)
     * @return A (partially) configured filter chain
     * @throws Exception If any error occurs
     */
    private HttpSecurity commonSecuredFilterChain(HttpSecurity http, String... requestMatchers) throws Exception {
        return http.cors(_ -> corsConfigurationSource()).csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(customCsrfSecurityFilter(), CsrfFilter.class)
                .securityMatchers(s -> s.requestMatchers(requestMatchers))
                .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(customAuthenticationEntryPoint()));
    }
}