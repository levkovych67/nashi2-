package com.nashi.dto.event;

import com.nashi.enums.EventStatus;
import com.nashi.enums.Region;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseDTO {

    private String id;
    private String title;
    private String description;
    private String ticketUrl;

    // --- Location Data ---
    private String venue;
    private String city;
    private Region region;

    private Double latitude;
    private Double longitude;
    private String geohash;

    // --- Meta ---
    private LocalDateTime dateTime;
    private EventStatus status;
    private String eventImageKey; // Or you can return a full pre-signed URL here
    private String artistName;
    private String slug;

}