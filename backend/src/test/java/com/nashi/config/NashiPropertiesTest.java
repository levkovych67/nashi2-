package com.nashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NashiPropertiesTest {

    @Test
    void defaultsMatchPublicConfigContract() {
        // docs/02-contracts.md 2.11
        NashiProperties props = bind(Map.of());

        assertEquals(15, props.audio().fragmentSeconds());
        assertEquals(new NashiProperties.MapProps(7, 10, 5, 17), props.map());
        assertEquals(new NashiProperties.LikesProps(50, 600), props.likes());
        assertEquals(new NashiProperties.SubmissionProps(5, 10, 30, 1000), props.submission());
        assertEquals(List.of(), props.cors().allowedOrigins());
        assertNull(props.security().ipHashSecret());
        assertNull(props.siteBaseUrl());
    }

    @Test
    void audioProvidersAreOffUnlessSwitchedOn() {
        assertEquals(new NashiProperties.ProvidersProps(new NashiProperties.ProviderProps(false),
                new NashiProperties.ProviderProps(false), new NashiProperties.ProviderProps(false)),
                bind(Map.of()).audio().providers());

        NashiProperties.ProvidersProps on = bind(Map.of(
                "nashi.audio.providers.deezer.enabled", "true",
                "nashi.audio.providers.commons.enabled", "true")).audio().providers();

        assertEquals(true, on.deezer().enabled());
        assertEquals(false, on.itunes().enabled());
        assertEquals(true, on.commons().enabled());
    }

    @Test
    void bindsKebabCaseKeys() {
        NashiProperties props = bind(Map.of(
                "nashi.cors.allowed-origins", "https://nashi.example,http://localhost:5173",
                "nashi.map.region-max-zoom", "8",
                "nashi.likes.flush-ms", "900",
                "nashi.security.ip-hash-secret", "s",
                "nashi.site-base-url", "https://nashi.example"));

        assertEquals(List.of("https://nashi.example", "http://localhost:5173"), props.cors().allowedOrigins());
        assertEquals(8, props.map().regionMaxZoom());
        assertEquals(10, props.map().placeMaxZoom());
        assertEquals(900, props.likes().flushMs());
        assertEquals("s", props.security().ipHashSecret());
        assertEquals("https://nashi.example", props.siteBaseUrl());
    }

    private static NashiProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("nashi", NashiProperties.class);
    }
}
