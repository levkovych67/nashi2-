package com.nashi.service.impl;

import com.nashi.domain.Artist;
import com.nashi.domain.Event;
import com.nashi.dto.MapPinDTO;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.EventStatus;
import com.nashi.enums.PinType;
import com.nashi.enums.Region;
import com.nashi.service.MapService;
import com.nashi.service.S3Service;
import com.nashi.util.ArtistFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {

    private final MongoTemplate mongoTemplate;
    private final S3Service s3Service;

    /**
     * Overload for backward compatibility or simple calls.
     * Defaults to fetching ALL types (Artists + Events).
     */
    @Override
    public List<MapPinDTO> getPins(Region region, String style, List<String> tags) {
        // Delegate to the main method with 'null' for types (implies all types)
        return getPins(region, style, tags, Collections.emptySet());
    }

    /**
     * Main implementation that supports filtering by PinType.
     */
    @Override
    public List<MapPinDTO> getPins(Region region, String style, List<String> tags, Set<PinType> types) {
        List<MapPinDTO> result = new ArrayList<>();

        // If types is null or empty, we fetch everything by default
        boolean fetchAll = (types == null || types.isEmpty());

        boolean shouldFetchArtists = fetchAll || types.contains(PinType.ARTIST);
        boolean shouldFetchEvents = fetchAll || types.contains(PinType.EVENT);

        // 1. Fetch Artists (Apply Style/Tags filters)
        if (shouldFetchArtists) {
            result.addAll(findArtists(region, style, tags));
        }

        // 2. Fetch Events (Apply Region & Date filters)
        // Note: Events ignore style/tags filters in this logic to keep the map populated
        if (shouldFetchEvents) {
            result.addAll(findEvents(region));
        }

        return result;
    }

    // --- Private Query Helpers ---

    private List<MapPinDTO> findArtists(Region region, String style, List<String> tags) {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(ArtistStatus.APPROVED));
        query.addCriteria(Criteria.where("location").ne(null)); // Must have coordinates

        if (region != null) {
            query.addCriteria(Criteria.where("region").is(region));
        }

        if (style != null && !style.isBlank()) {
            // Matched literally (case-insensitive substring): "a.*" must not become a regex.
            query.addCriteria(Criteria.where("style").regex(Pattern.quote(style), "i"));
        }

        if (tags != null && !tags.isEmpty()) {
            query.addCriteria(Criteria.where("musicTags").in(tags));
        }

        return mongoTemplate.find(query, Artist.class).stream()
                .map(this::artistToPinDto)
                .toList();
    }

    private List<MapPinDTO> findEvents(Region region) {
        Query query = new Query();
        query.addCriteria(Criteria.where("status").is(EventStatus.APPROVED));
        query.addCriteria(Criteria.where("location").ne(null)); // Must have coordinates

        // Only show future events
        query.addCriteria(Criteria.where("dateTime").gt(LocalDateTime.now()));

        if (region != null) {
            query.addCriteria(Criteria.where("region").is(region));
        }

        // Limit results to avoid cluttering the map with too many future events
        query.with(Sort.by(Sort.Direction.ASC, "dateTime"));
        query.limit(100);

        return mongoTemplate.find(query, Event.class).stream()
                .map(this::eventToPinDto)
                .toList();
    }

    // --- Mappers ---

    // Package-private for tests
    MapPinDTO artistToPinDto(Artist artist) {

        String avatarUrl = null;
        if (artist.getImageKeys() != null && !artist.getImageKeys().isEmpty()) {
            if(artist.getImageKeys().get(0).contains("artists/")){
                avatarUrl = s3Service.getFileUrl(artist.getImageKeys().get(0));
            }
            else  {
                avatarUrl = artist.getImageKeys().get(0);
            }
        }

        String audioUrl = (artist.getDemoTrackKeys() != null && !artist.getDemoTrackKeys().isEmpty())
                ? s3Service.getFileUrl(artist.getDemoTrackKeys().get(0))
                : null;

        return MapPinDTO.builder()
                .id(artist.getId())
                .type(PinType.ARTIST)
                .name(artist.getName())
                .style(artist.getStyle())
                .latitude(artist.getLocation().getY()) // Y = Latitude
                .longitude(artist.getLocation().getX()) // X = Longitude
                .avatarUrl(avatarUrl)
                .audioUrl(audioUrl)
                .slug(artist.getSlug())
                .categories(ArtistFacts.categoriesOf(artist))
                .region(artist.getRegion())
                .city(artist.getCity())
                .year(ArtistFacts.yearOf(artist))
                .genres(ArtistFacts.genresOf(artist))
                .build();
    }

    // Package-private for tests
    MapPinDTO eventToPinDto(Event event) {
        String imageUrl = (event.getEventImageKey() != null)
                ? s3Service.getFileUrl(event.getEventImageKey())
                : null;

        return MapPinDTO.builder()
                .id(event.getId())
                .type(PinType.EVENT)
                .name(event.getTitle()) // Map Title -> Name
                .style(event.getVenue()) // Map Venue -> Style (for display on pin)
                .latitude(event.getLocation().getY())
                .longitude(event.getLocation().getX())
                .avatarUrl(imageUrl)
                .audioUrl(null)
                .slug(event.getSlug())
                // So the map's region filter works for events too
                .region(event.getRegion())
                .city(event.getCity())
                .build();
    }
}