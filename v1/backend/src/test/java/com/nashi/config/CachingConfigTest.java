package com.nashi.config;

import com.nashi.enums.Region;
import com.nashi.service.impl.GeoLookupServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CachingConfigTest {

    @Test
    void cachingIsOn() {
        assertNotNull(CachingConfig.class.getAnnotation(Configuration.class));
        assertNotNull(CachingConfig.class.getAnnotation(EnableCaching.class));
    }

    @Test
    void emptyCityResultsAreNotCached() throws Exception {
        // searchCity answers [] when the AI call fails; caching that would hide the city until a restart.
        Cacheable cacheable = GeoLookupServiceImpl.class
                .getMethod("searchCity", Region.class, String.class)
                .getAnnotation(Cacheable.class);

        assertArrayEquals(new String[]{"cityCoordinates"}, cacheable.value());
        assertEquals("#result == null || #result.isEmpty()", cacheable.unless());
    }
}
