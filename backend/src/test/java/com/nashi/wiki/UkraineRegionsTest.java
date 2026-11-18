package com.nashi.wiki;

import com.nashi.geo.Region;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UkraineRegionsTest {

    @Test
    void hasAllTwentySevenUnits() {
        assertThat(UkraineRegions.size()).isEqualTo(27);
    }

    @Test
    void reachesEveryRegionExactlyOnce() {
        assertThat(EnumSet.copyOf(UkraineRegions.regions())).isEqualTo(EnumSet.allOf(Region.class));
        assertThat(UkraineRegions.regions()).hasSize(27);
    }

    @Test
    void mapsTheCitiesWithSpecialStatusToTheirOwnRegions() {
        assertThat(UkraineRegions.fromQid("Q1899")).isEqualTo(Optional.of(Region.KYIV_CITY));
        assertThat(UkraineRegions.fromQid("Q7525")).isEqualTo(Optional.of(Region.SEVASTOPOL_CITY));
        assertThat(UkraineRegions.fromQid("Q170036")).isEqualTo(Optional.of(Region.KYIV));
        assertThat(UkraineRegions.fromQid("Q756294")).isEqualTo(Optional.of(Region.CRIMEA));
        assertThat(UkraineRegions.fromQid("Q164193")).isEqualTo(Optional.of(Region.LVIV));
        assertThat(UkraineRegions.fromQid("Q159")).isEmpty();
    }

    @Test
    void qidOfIsTheInverseOfFromQid() {
        for (Region region : Region.values()) {
            assertThat(UkraineRegions.fromQid(UkraineRegions.qidOf(region))).contains(region);
        }
    }

    @Test
    void listsTwentyFiveOblastsWithoutTheTwoCities() {
        List<String> oblasts = UkraineRegions.listOblastQids();

        assertThat(oblasts).hasSize(25).doesNotHaveDuplicates()
                .doesNotContain("Q1899", "Q7525")
                .contains("Q170036", "Q756294", "Q164193");
        assertThat(new HashSet<>(UkraineRegions.listCityQids())).containsExactlyInAnyOrder("Q1899", "Q7525");
    }

    @Test
    void oblastsAreListedInContractOrder() {
        List<Region> order = UkraineRegions.listOblastQids().stream()
                .map(qid -> UkraineRegions.fromQid(qid).orElseThrow())
                .toList();

        assertThat(order).isSorted();
    }
}
