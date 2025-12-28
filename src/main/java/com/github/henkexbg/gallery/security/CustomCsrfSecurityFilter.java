package com.github.henkexbg.gallery.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Custom logic for CSRF checks. Instead of relying on a CSRF token, this filter relies on the Sec-Fetch-Site header, and if a cross-site
 * request is detected, it will only be allowed if it's from a whitelisted domain, similar to CORS logic. This filter ONLY checks the host
 * though, not the protocol or port. The originating domain will be retrieved from either the Origin or Referer header, whichever is
 * populated.
 */
public class CustomCsrfSecurityFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME_SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final String HEADER_NAME_ORIGIN = "Origin";
    private static final String HEADER_NAME_REFERER = "Referer";
    private static final Set<String> UNSAFE_METHODS = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Value("#{'${gallery.web.crossOrigin.allowedHosts}'.split(',')}")
    Set<String> allowedHosts;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String secFetchSite = request.getHeader(HEADER_NAME_SEC_FETCH_SITE);
        String method = request.getMethod();
        if ("cross-site".equalsIgnoreCase(secFetchSite) && UNSAFE_METHODS.contains(method.toUpperCase())) {
            String originUrlString = request.getHeader(HEADER_NAME_ORIGIN);
            if (originUrlString == null) {
                originUrlString = request.getHeader(HEADER_NAME_REFERER);
            }
            if (originUrlString == null) {
                sendError("%s or %s header is required if %s is cross-site".formatted(HEADER_NAME_ORIGIN, HEADER_NAME_REFERER,
                        HEADER_NAME_SEC_FETCH_SITE), response);
                return;
            }
            String originHost;
            try {
                URI originUrl = URI.create(originUrlString);
                originHost = originUrl.getHost();
            } catch (IllegalArgumentException e) {
                originHost = null;
            }
            if (originHost == null || !allowedHosts.contains(originHost)) {
                sendError("Invalid domain in CSRF check: %s".formatted(originHost), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void sendError(String errorMessage, HttpServletResponse response) throws IOException {
        LOG.info("CSRF check failed: {}", errorMessage);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.getWriter().write(errorMessage);
        response.getWriter().flush();
    }

}