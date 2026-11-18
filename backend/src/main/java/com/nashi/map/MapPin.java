package com.nashi.map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nashi.artist.MusicianKind;
import com.nashi.geo.Region;

/** One pin of {@code GET /map/pins}; {@code thumbUrl} is absent when the artist has no photo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapPin(String id, String slug, String name, MusicianKind kind, double lat, double lon, Region region,
                     String placeId, String thumbUrl, boolean hasAudio) {
}
