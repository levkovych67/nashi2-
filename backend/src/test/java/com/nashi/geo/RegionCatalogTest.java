package com.nashi.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegionCatalogTest {

    private static RegionCatalog catalog;

    @BeforeAll
    static void load() {
        catalog = new RegionCatalog();
        catalog.load();
    }

    @Test
    void catalogCoversEveryRegion() {
        assertThat(catalog.all()).hasSize(27);
        for (Region r : Region.values()) {
            assertThat(catalog.of(r).code()).isEqualTo(r);
        }
    }

    @Test
    void catalogNameEqualsUaNameForAllRegions() {
        for (Region r : Region.values()) {
            assertThat(catalog.of(r).name()).isEqualTo(r.uaName());
        }
    }

    @Test
    void centerLiesInsideBbox() {
        for (RegionMeta m : catalog.all()) {
            double[] c = m.center();
            double[] b = m.bbox();
            assertThat(c[0]).as(m.code() + " lon").isBetween(b[0], b[2]);
            assertThat(c[1]).as(m.code() + " lat").isBetween(b[1], b[3]);
        }
    }

    @Test
    void kyivCityBboxInsideKyivOblastBbox() {
        double[] city = catalog.of(Region.KYIV_CITY).bbox();
        double[] oblast = catalog.of(Region.KYIV).bbox();
        assertThat(city[0]).isGreaterThanOrEqualTo(oblast[0]);
        assertThat(city[1]).isGreaterThanOrEqualTo(oblast[1]);
        assertThat(city[2]).isLessThanOrEqualTo(oblast[2]);
        assertThat(city[3]).isLessThanOrEqualTo(oblast[3]);
    }

    @Test
    void regionMetaComparesArraysByContent() {
        RegionMeta a = new RegionMeta(Region.LVIV, "x", new double[] {1, 2}, new double[] {0, 0, 3, 3});
        RegionMeta b = new RegionMeta(Region.LVIV, "x", new double[] {1, 2}, new double[] {0, 0, 3, 3});
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toString()).contains("[1.0, 2.0]");
    }

    @Test
    void failsOnUnknownResource() {
        assertThatThrownBy(() -> new RegionCatalog("geo/missing.json").load()).isInstanceOf(IllegalStateException.class);
    }
}
