package com.nashi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS for {@code /api/**}: only the origins in {@code nashi.cors.allowed-origins} (exact origins or patterns such
 * as {@code https://*.nashi.example}), GET and POST, no credentials (the API is public and uses no cookies).
 * {@code Retry-After} and {@code ETag} are exposed so a cross-origin client can honour 429 and send
 * {@code If-None-Match}. With no origins configured every cross-origin request is refused.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(NashiProperties properties) {
        this.allowedOrigins = properties.cors().allowedOrigins();
    }

    @Bean
    public CorsFilter corsFilter() {
        return corsFilter(allowedOrigins);
    }

    static CorsFilter corsFilter(List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.ETAG));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
