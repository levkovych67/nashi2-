package com.nashi.geo;

import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceRefTest {

    @Test
    void ofMapsPlaceIdCoordinatesTypeAndArea() {
        Place kyiv = new Place("Q1899", "Київ", "київ", PlaceType.CITY, Region.KYIV_CITY,
                new GeoJsonPoint(30.5236, 50.45), 839.0, 2_952_301L);

        PlaceRef ref = PlaceRef.of(kyiv);

        assertThat(ref).isEqualTo(new PlaceRef("Q1899", 50.45, 30.5236, PlaceType.CITY, 839.0));
    }

    @Test
    void ofKeepsNullArea() {
        Place brody = new Place("Q465104", "Броди", "броди", PlaceType.TOWN, Region.LVIV,
                new GeoJsonPoint(25.154166666, 50.078055555), null, null);

        assertThat(PlaceRef.of(brody).areaKm2()).isNull();
    }
}
