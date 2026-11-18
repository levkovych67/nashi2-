package com.nashi.place;

import com.nashi.geo.Region;

/** One autocomplete answer of {@code GET /api/v2/places/search} (OpenAPI schema {@code PlaceSuggestion}). */
public record PlaceSuggestion(
        String placeId, String name, PlaceType type, Region region, String regionName, double lat, double lon) {

    public static PlaceSuggestion of(Place place) {
        return new PlaceSuggestion(place.id(), place.name(), place.type(), place.region(), place.region().uaName(),
                place.lat(), place.lon());
    }
}
