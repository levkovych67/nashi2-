package com.nashi.artist;

import com.nashi.geo.Region;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/** Where the artist is from; {@code center} is the settlement's own point, the map pin is {@code Artist.pin}. */
public record Origin(String placeId, String placeName, Region region, OriginBasis basis, PinPrecision precision,
                     GeoJsonPoint center) {
}
