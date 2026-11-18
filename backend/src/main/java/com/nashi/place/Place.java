package com.nashi.place;

import com.nashi.geo.Region;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A Ukrainian settlement ({@code docs/02-contracts.md} section 3, {@code places}). {@code id} is the Wikidata QID;
 * {@code point} is GeoJSON, so {@code x} is the longitude and {@code y} the latitude. {@code areaKm2} and
 * {@code population} are {@code null} when Wikidata does not know them.
 */
@Document("places")
public record Place(
        @Id String id,
        String name,
        String nameNormalized,
        PlaceType type,
        Region region,
        GeoJsonPoint point,
        Double areaKm2,
        Long population) {

    public double lat() {
        return point.getY();
    }

    public double lon() {
        return point.getX();
    }
}
