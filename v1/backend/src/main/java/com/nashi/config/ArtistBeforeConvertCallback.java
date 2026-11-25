package com.nashi.config;

import com.nashi.util.StringUtils;

import com.nashi.domain.Artist;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;
import ch.hsr.geohash.GeoHash; // Still using the library you added

@Component
public class ArtistBeforeConvertCallback implements BeforeConvertCallback<Artist> {

    @Override
    public Artist onBeforeConvert(Artist artist, String collection) {
        // 1. Generate Slug, only when there is none: a slug set by the caller (e.g. a free one for a suggestion) stays
        if ((artist.getSlug() == null || artist.getSlug().isBlank()) && artist.getName() != null) {
            artist.setSlug(StringUtils.toSlug(artist.getName()));
        }

        // 2. Handle Geo Logic
        // We assume the DTO passed 'location' as a GeoJsonPoint or raw lat/lon
        if (artist.getLocation() != null) {
            double lat = artist.getLocation().getY();
            double lon = artist.getLocation().getX();
            
            // Auto-calculate the string geohash for your frontend
            artist.setGeohash(GeoHash.withCharacterPrecision(lat, lon, 12).toBase32());
        }

        return artist;
    }

}