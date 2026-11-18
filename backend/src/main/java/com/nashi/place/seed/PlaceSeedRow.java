package com.nashi.place.seed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nashi.artist.Names;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * One line of {@code geo/places-ua.json.gz}: a settlement as the generator found it in Wikidata. {@code areaKm2}
 * and {@code population} are left out of the JSON when unknown.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceSeedRow(
        String qid, String name, PlaceType type, Region region, double lat, double lon, Double areaKm2,
        Long population) {

    public Place toPlace() {
        return new Place(qid, name, Names.normalize(name), type, region, new GeoJsonPoint(lon, lat), areaKm2,
                population);
    }
}
