package com.nashi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.domain.Artist;
import com.nashi.domain.Event;
import com.nashi.domain.Member;
import com.nashi.dto.MapPinDTO;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.PinType;
import com.nashi.enums.Region;
import com.nashi.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapServiceImplTest {

    private S3Service s3Service;
    private MongoTemplate mongoTemplate;
    private MapServiceImpl service;

    @BeforeEach
    void setUp() {
        s3Service = mock(S3Service.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new MapServiceImpl(mongoTemplate, s3Service);
    }

    @Test
    void artistPinCarriesFilterFields() {
        when(s3Service.getFileUrl("artists/images/shevchenko.jpg"))
                .thenReturn("https://cdn.test/artists/images/shevchenko.jpg");
        Artist writer = Artist.builder()
                .id("p1")
                .name("Тарас Шевченко")
                .slug("taras-shevchenko")
                .categories(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST))
                .region(Region.CHERKASY)
                .city("Моринці")
                .birthDate("1814-03-09")
                .genres(List.of("поезія", "живопис"))
                .location(new GeoJsonPoint(31.0, 49.2))
                .imageKeys(List.of("artists/images/shevchenko.jpg"))
                .build();

        MapPinDTO pin = service.artistToPinDto(writer);

        assertEquals(PinType.ARTIST, pin.getType());
        assertEquals(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST), pin.getCategories());
        assertEquals(Region.CHERKASY, pin.getRegion());
        assertEquals("Моринці", pin.getCity());
        assertEquals(1814, pin.getYear());
        assertEquals(List.of("поезія", "живопис"), pin.getGenres());
        assertEquals(49.2, pin.getLatitude());
        assertEquals(31.0, pin.getLongitude());
        assertEquals("https://cdn.test/artists/images/shevchenko.jpg", pin.getAvatarUrl());
    }

    @Test
    void formBandPinFallsBackToMembersStyleAndFoundationYear() {
        Artist band = Artist.builder()
                .id("p2")
                .name("Гурт з форми")
                .style("Рок")
                .region(Region.LVIV)
                .city("Львів")
                .foundationYear(1994)
                .members(List.of(Member.builder().firstName("Святослав").role("вокал").build()))
                .location(new GeoJsonPoint(24.03, 49.84))
                .build();

        MapPinDTO pin = service.artistToPinDto(band);

        assertEquals(List.of(ArtistCategory.BAND), pin.getCategories());
        assertEquals(List.of("Рок"), pin.getGenres());
        assertEquals(1994, pin.getYear());
    }

    @Test
    void pinJsonLeavesOutEmptyFields() {
        Artist solo = Artist.builder()
                .id("p3")
                .name("Соло без року")
                .location(new GeoJsonPoint(30.52, 50.45))
                .build();

        JsonNode json = new ObjectMapper().valueToTree(service.artistToPinDto(solo));

        assertEquals("MUSICIAN", json.get("categories").get(0).asText());
        assertFalse(json.has("genres"));
        assertFalse(json.has("year"));
        assertFalse(json.has("region"));
        assertFalse(json.has("city"));
        assertFalse(json.has("audioUrl"));
        assertFalse(json.has("avatarUrl"));
    }

    @Test
    void styleIsMatchedLiterally() {
        when(mongoTemplate.find(any(Query.class), eq(Artist.class))).thenReturn(List.of());

        service.getPins(null, "a.*", null, Set.of(PinType.ARTIST));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Artist.class));
        Object style = query.getValue().getQueryObject().get("style");
        Pattern pattern = assertInstanceOf(Pattern.class, style);
        assertEquals(Pattern.quote("a.*"), pattern.pattern());
        assertTrue((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0);
        assertTrue(pattern.matcher("A.*").find());
        assertFalse(pattern.matcher("abc").find());
    }

    @Test
    void eventPinCarriesRegion() {
        Event event = Event.builder()
                .id("e1")
                .title("Концерт")
                .venue("Malevich")
                .city("Львів")
                .region(Region.LVIV)
                .slug("kontsert")
                .location(new GeoJsonPoint(24.03, 49.84))
                .build();
        Event noPlace = Event.builder()
                .id("e2")
                .title("Без регіону")
                .location(new GeoJsonPoint(30.52, 50.45))
                .build();

        MapPinDTO pin = service.eventToPinDto(event);
        JsonNode bare = new ObjectMapper().valueToTree(service.eventToPinDto(noPlace));

        assertEquals(PinType.EVENT, pin.getType());
        assertEquals(Region.LVIV, pin.getRegion());
        assertEquals("Львів", pin.getCity());
        assertFalse(bare.has("region"));
        assertFalse(bare.has("city"));
    }
}
