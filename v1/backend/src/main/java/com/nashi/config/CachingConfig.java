package com.nashi.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on {@code @Cacheable} (city lookups in {@code GeoLookupServiceImpl}). Spring Boot then provides an in-memory
 * {@code ConcurrentMapCacheManager}; caches live until the next restart.
 */
@Configuration
@EnableCaching
public class CachingConfig {
}
