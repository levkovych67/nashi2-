package com.nashi.mapper;

import com.nashi.domain.Event;
import com.nashi.dto.event.EventCreateRequestDTO;
import com.nashi.dto.event.EventResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

public class EventMapper {

    public static Event toEntity(EventCreateRequestDTO request) {
        if (request == null) {
            return null;
        }

        return Event.builder()
                .title(request.getTitle())
                .artistName(request.getArtistName())
                .description(request.getDescription())
                .ticketUrl(request.getTicketUrl())
                .venue(request.getVenue())
                .city(request.getCity())
                .region(request.getRegion())
                .dateTime(request.getDateTime())
                .build();
    }

    public static EventResponseDTO toResponseDTO(Event event) {
        if (event == null) {
            return null;
        }

        EventResponseDTO.EventResponseDTOBuilder builder = EventResponseDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .ticketUrl(event.getTicketUrl())
                .venue(event.getVenue())
                .city(event.getCity())
                .geohash(event.getGeohash())
                .region(event.getRegion())
                .dateTime(event.getDateTime())
                .status(event.getStatus())
                .eventImageKey(event.getEventImageKey())
                .artistName(event.getArtistName())
                .slug(event.getSlug());

        // Extract Latitude (Y) and Longitude (X) from GeoJsonPoint
        if (event.getLocation() != null) {
            builder.latitude(event.getLocation().getY());  // Y is usually Latitude
            builder.longitude(event.getLocation().getX()); // X is usually Longitude
        }

        return builder.build();
    }


}