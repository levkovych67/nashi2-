package com.nashi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Global CORS configuration for the Nashi API.
 * Allowed origins come from {@code nashi.cors.allowed-origins}, a comma list of origins or origin patterns
 * (e.g. {@code https://nashi.example,https://*.nashi.example}); {@code *} or a missing value allows any origin.
 * Credentials are never allowed: the API is public and uses no cookies.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${nashi.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOrigins = parse(allowedOrigins);
    }

    private static List<String> parse(String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        return origins.isEmpty() ? List.of(CorsConfiguration.ALL) : origins;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Patterns, so "*" and "https://*.example" work as well as exact origins
        config.setAllowedOriginPatterns(allowedOrigins);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(false);
        config.addExposedHeader("*");

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS config to all endpoints
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
