package com.nashi.wiki;

import com.nashi.enums.Region;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UkraineRegionsTest {

    @Test
    void hasAllTwentySevenUnits() {
        assertEquals(27, UkraineRegions.size());
    }

    @Test
    void reachesEveryRegion() {
        Set<Region> reached = EnumSet.copyOf(UkraineRegions.regions());
        assertEquals(EnumSet.allOf(Region.class), reached);
    }

    @Test
    void mapsTheCitiesWithSpecialStatus() {
        assertEquals(Optional.of(Region.KYIV), UkraineRegions.fromQid("Q1899"));
        assertEquals(Optional.of(Region.CRIMEA), UkraineRegions.fromQid("Q7525"));
        assertEquals(Optional.of(Region.LVIV), UkraineRegions.fromQid("Q164193"));
        assertEquals(Optional.empty(), UkraineRegions.fromQid("Q159"));
    }
}
