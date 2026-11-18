package com.nashi.place;

import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceStoreIT extends MongoIT {

    @Autowired
    private PlaceStore store;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void seed() {
        PlaceFixtures.clear(mongo);
        PlaceFixtures.insertAll(mongo);
    }

    @AfterEach
    void clear() {
        PlaceFixtures.clear(mongo);
    }

    @Test
    void findReturnsSeededPlace() {
        assertThat(store.find("Q36036")).contains(PlaceFixtures.LVIV);
        assertThat(store.find("Q0")).isEmpty();
    }

    @Test
    void ensureUpsertsOtherWhenMissing() {
        Place created = store.ensure("Q2054219", "Кам’янка", Region.TERNOPIL, 49.1, 25.5);

        Place expected = new Place("Q2054219", "Кам’янка", "камянка", PlaceType.OTHER, Region.TERNOPIL,
                new GeoJsonPoint(25.5, 49.1), null, null);
        assertThat(created).isEqualTo(expected);
        assertThat(store.find("Q2054219")).contains(expected);
    }

    @Test
    void ensureLeavesAnExistingPlaceUnchanged() {
        Place existing = store.ensure("Q36036", "Lemberg", Region.KYIV, 0.0, 0.0);

        assertThat(existing).isEqualTo(PlaceFixtures.LVIV);
        assertThat(store.find("Q36036")).contains(PlaceFixtures.LVIV);
    }

    @Test
    void ensureTwiceCreatesOnePlace() {
        store.ensure("Q2054219", "Нова Гута", Region.TERNOPIL, 49.1, 25.5);
        store.ensure("Q2054219", "Інша назва", Region.LVIV, 48.0, 24.0);

        assertThat(store.find("Q2054219")).get().extracting(Place::name, Place::region)
                .containsExactly("Нова Гута", Region.TERNOPIL);
    }

    @Test
    void nearestWithinRadius() {
        // About 2.2 km north-east of Brody's centre; Lviv is ~85 km away
        assertThat(store.nearest(50.095, 25.175, 5_000)).contains(PlaceFixtures.BRODY);
        // Between Lviv (~33 km) and Brody (~55 km): the closer one wins
        assertThat(store.nearest(49.95, 24.45, 100_000)).contains(PlaceFixtures.LVIV);
    }

    @Test
    void nearestIsEmptyBeyondTheRadius() {
        assertThat(store.nearest(50.095, 25.175, 1_000)).isEmpty();
        assertThat(store.nearest(46.0, 33.0, 15_000)).isEmpty();
    }
}
