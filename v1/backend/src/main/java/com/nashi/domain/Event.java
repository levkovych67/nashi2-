package com.nashi.domain;

import com.nashi.enums.EventStatus;
import com.nashi.enums.Region;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    private String id;

    // --- Basic Info ---
    private String title;       // e.g., "Charity Concert", "Album Presentation"
    private String description; // Additional info about lineup or restrictions
    private String ticketUrl;   // Link to tickets or "Monobank jar"

    // --- Location ---
    private String venue;       // e.g., "Malevich", "Kyiv Cybersport Arena"
    private String city;        // e.g., "Lviv", "Drohobych" (Text field for display)
    
    @Indexed
    private Region region;      // Enum for filtering (e.g., LVIV, KYIV)

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    @Indexed
    private String geohash;
    // --- Time ---
    @Indexed
    private LocalDateTime dateTime; // Sortable date

    // --- Moderation ---
    @Builder.Default
    @Indexed
    private EventStatus status = EventStatus.PENDING; // Default status is PENDING

    private String eventImageKey;
    private String artistName;  // Display name
    private String slug;

}