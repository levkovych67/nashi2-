package com.nashi.geo;

import com.nashi.place.Place;
import com.nashi.place.PlaceType;

/** The part of a {@link Place} that pin placement needs. */
public record PlaceRef(String placeId, double lat, double lon, PlaceType type, Double areaKm2) {

    public static PlaceRef of(Place place) {
        return new PlaceRef(place.id(), place.lat(), place.lon(), place.type(), place.areaKm2());
    }
}
