package com.nashi.config;

import ch.hsr.geohash.GeoHash;
import com.nashi.domain.Artist;
import com.nashi.domain.Event;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class EventBeforeConvertCallback implements BeforeConvertCallback<Event> {

    @Override
    public Event onBeforeConvert(Event event, String collection) {
        // 1. Generate Slug
        if (event.getSlug() == null && event.getTitle() != null) {
            event.setSlug(toSlug(event.getTitle()));
        }

        // 2. Handle Geo Logic
        // We assume the DTO passed 'location' as a GeoJsonPoint or raw lat/lon
        if (event.getLocation() != null) {
            double lat = event.getLocation().getY();
            double lon = event.getLocation().getX();
            
            // Auto-calculate the string geohash for your frontend
            event.setGeohash(GeoHash.withCharacterPrecision(lat, lon, 12).toBase32());
        }

        return event;
    }

    private String toSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\u0400-\\u04FF]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}