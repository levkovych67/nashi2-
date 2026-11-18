package com.nashi.map;

import com.nashi.geo.Region;
import com.nashi.place.PlaceType;

/** Musicians of one settlement; {@code lat}/{@code lon} are the settlement's own point, not a pin. */
public record PlaceStat(String placeId, String name, Region region, PlaceType type, double lat, double lon,
                        long count) {
}
