package com.nashi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.PinType;
import com.nashi.enums.Region;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** A map pin. Null and empty fields are left out of the JSON to keep the pin list small. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MapPinDTO {
    private String id;
    private String name;      // Artist Name or Event Title
    private String style;     // Artist Style or Event Venue
    private Double latitude;
    private Double longitude;
    private String avatarUrl;
    private String audioUrl;  // Null for Events
    private String slug;
    private PinType type;     // <--- NEW: EVENT or ARTIST

    // --- Filter fields ---
    /** Artists only. */
    private List<ArtistCategory> categories;
    /** Artists and events. */
    private Region region;
    /** Artists and events. */
    private String city;
    /** Artists only: birth year of a person, foundation year of a band. */
    private Integer year;
    /** Artists only. */
    private List<String> genres;
}