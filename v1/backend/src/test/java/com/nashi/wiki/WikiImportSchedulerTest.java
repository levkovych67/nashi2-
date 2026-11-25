package com.nashi.wiki;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiImportSchedulerTest {

    @Test
    void usesAZoneIdThatEveryJdk17Knows() throws NoSuchMethodException {
        // "Europe/Kyiv" exists only in tzdata 2022b+ (JDK 17.0.5+); an unknown zone stops the app from starting.
        String zone = WikiImportScheduler.class.getMethod("monthly").getAnnotation(Scheduled.class).zone();

        assertEquals("Europe/Kiev", zone);
        assertDoesNotThrow(() -> ZoneId.of(zone));
    }
}
