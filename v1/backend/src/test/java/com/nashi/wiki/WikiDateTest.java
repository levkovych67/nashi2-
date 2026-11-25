package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WikiDateTest {

    @Test
    void dayPrecisionKeepsTheFullDate() {
        assertEquals(new WikiDate("1814-03-09", 1814), WikiDate.of("+1814-03-09T00:00:00Z", 11));
    }

    @Test
    void monthPrecisionKeepsYearAndMonth() {
        assertEquals(new WikiDate("1814-03", 1814), WikiDate.of("+1814-03-09T00:00:00Z", 10));
    }

    @Test
    void yearPrecisionKeepsTheYear() {
        assertEquals(new WikiDate("1814", 1814), WikiDate.of("+1814-03-09T00:00:00Z", 9));
    }

    @Test
    void coarserThanAYearIsDropped() {
        assertNull(WikiDate.of("+1800-00-00T00:00:00Z", 8));
        assertNull(WikiDate.of("+1800-00-00T00:00:00Z", 7));
    }

    @Test
    void negativeOrBrokenTimesAreDropped() {
        assertNull(WikiDate.of("-0100-01-01T00:00:00Z", 11));
        assertNull(WikiDate.of("garbage", 11));
        assertNull(WikiDate.of(null, 11));
    }

    private static final String JULIAN = "http://www.wikidata.org/entity/Q1985786";
    private static final String GREGORIAN = "http://www.wikidata.org/entity/Q1985727";

    @Test
    void julianDayDateConvertsToGregorian() {
        assertEquals(new WikiDate("1814-03-09", 1814), WikiDate.of("+1814-02-25T00:00:00Z", 11, JULIAN));
        assertEquals(new WikiDate("1861-03-10", 1861), WikiDate.of("+1861-02-26T00:00:00Z", 11, JULIAN));
    }

    @Test
    void julianAcrossCenturyLeapDay() {
        assertEquals(new WikiDate("1900-03-13", 1900), WikiDate.of("+1900-02-29T00:00:00Z", 11, JULIAN));
    }

    @Test
    void gregorianUnchanged() {
        assertEquals(new WikiDate("1814-03-09", 1814), WikiDate.of("+1814-03-09T00:00:00Z", 11, GREGORIAN));
        assertEquals(new WikiDate("1814-03-09", 1814), WikiDate.of("+1814-03-09T00:00:00Z", 11, null));
    }

    @Test
    void julianYearPrecisionUnchanged() {
        assertEquals(new WikiDate("1814", 1814), WikiDate.of("+1814-01-01T00:00:00Z", 9, JULIAN));
        assertEquals(new WikiDate("1814-02", 1814), WikiDate.of("+1814-02-01T00:00:00Z", 10, JULIAN));
    }
}
