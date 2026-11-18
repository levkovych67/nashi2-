package com.nashi.map;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static com.nashi.map.MapFixtures.KYIV_ID;
import static com.nashi.map.MapFixtures.LVIV_ID;
import static com.nashi.map.MapFixtures.artist;
import static org.assertj.core.api.Assertions.assertThat;

class MapServiceIT extends MongoIT {

    @Autowired
    private MapService service;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ApplicationEventPublisher events;

    @BeforeEach
    void seed() {
        clear();
        mongo.save(new Place(LVIV_ID, "Львів", "львів", PlaceType.CITY, Region.LVIV,
                new GeoJsonPoint(24.0315921, 49.841952), 182.0, 717_273L));
        mongo.save(new Place(KYIV_ID, "Київ", "київ", PlaceType.CITY, Region.KYIV_CITY,
                new GeoJsonPoint(30.5236, 50.45), 848.0, 2_952_301L));
        artists.save(artist("lviv-a", Region.LVIV, LVIV_ID, "Львів").build());
        artists.save(artist("lviv-b", Region.LVIV, LVIV_ID, "Львів").build());
        artists.save(artist("kyiv-a", Region.KYIV_CITY, KYIV_ID, "Київ").build());
        artists.save(artist("hidden", Region.LVIV, LVIV_ID, "Львів").status(ArtistStatus.HIDDEN).build());
        artists.save(artist("no-pin", Region.ODESA, "Q1", "Одеса").pin(null).build());
        changed();
    }

    @AfterEach
    void clear() {
        artists.deleteAll();
        mongo.remove(new Query(Criteria.where("_id").in(LVIV_ID, KYIV_ID)), Place.class);
    }

    private void changed() {
        events.publishEvent(new ArtistChangedEvent("any"));
    }

    @Test
    void pinsContainOnlyPublishedWithPin() {
        assertThat(service.snapshot().pins()).extracting(MapPin::slug)
                .containsExactlyInAnyOrder("lviv-a", "lviv-b", "kyiv-a");
    }

    @Test
    void artistWithoutPinIsNotOnMapButHasPage() {
        assertThat(service.snapshot().pins()).extracting(MapPin::slug).doesNotContain("no-pin");
        assertThat(artists.findBySlugAndStatus("no-pin", ArtistStatus.PUBLISHED)).isPresent();
    }

    @Test
    void regionsAlways27WithCounts() {
        var regions = service.snapshot().regions();
        assertThat(regions).hasSize(27);
        assertThat(regions).filteredOn(r -> r.code() == Region.LVIV).extracting(RegionStat::count).containsExactly(2L);
        assertThat(regions).filteredOn(r -> r.code() == Region.KYIV_CITY).extracting(RegionStat::count)
                .containsExactly(1L);
        assertThat(regions).filteredOn(r -> r.code() != Region.LVIV && r.code() != Region.KYIV_CITY)
                .allMatch(r -> r.count() == 0);
        RegionStat lviv = regions.stream().filter(r -> r.code() == Region.LVIV).findFirst().orElseThrow();
        assertThat(lviv.name()).isNotBlank();
        assertThat(lviv.center()).hasSize(2);
        assertThat(lviv.bbox()).hasSize(4);
    }

    @Test
    void placesAggregateByPlaceId() {
        var places = service.snapshot().places();
        assertThat(places).extracting(PlaceStat::placeId).containsExactlyInAnyOrder(LVIV_ID, KYIV_ID);
        PlaceStat lviv = places.stream().filter(p -> p.placeId().equals(LVIV_ID)).findFirst().orElseThrow();
        assertThat(lviv.count()).isEqualTo(2);
        assertThat(lviv.name()).isEqualTo("Львів");
        assertThat(lviv.type()).isEqualTo(PlaceType.CITY);
        assertThat(lviv.region()).isEqualTo(Region.LVIV);
        assertThat(lviv.lat()).isEqualTo(49.841952);
        assertThat(lviv.lon()).isEqualTo(24.0315921);
    }

    @Test
    void versionChangesAfterArtistChangedEvent() {
        String before = service.snapshot().version();
        assertThat(before).matches("[0-9a-f]{8}");
        artists.save(artist("lviv-c", Region.LVIV, LVIV_ID, "Львів").build());

        assertThat(service.snapshot().version()).as("cached until the event").isEqualTo(before);
        changed();
        assertThat(service.snapshot().version()).isNotEqualTo(before);
        assertThat(service.snapshot().pins()).hasSize(4);
    }

    @Test
    void versionStableAfterLikes() {
        String before = service.snapshot().version();
        mongo.updateFirst(Query.query(Criteria.where("slug").is("lviv-a")), new Update().inc("likesCount", 1),
                Artist.class);
        changed();
        assertThat(service.snapshot().version()).isEqualTo(before);
    }

    @Autowired
    private com.nashi.geo.RegionCatalog regionCatalog;
    @Autowired
    private com.nashi.place.PlaceStore placeStore;
    @Autowired
    private com.nashi.media.FileStorage fileStorage;

    @Test
    void pinHasAudioIgnoresClipsOfDisabledSources() {
        artists.save(MapFixtures.artist("deezer-only", Region.LVIV, LVIV_ID, "Львів")
                .audio(java.util.List.of(new com.nashi.artist.AudioClip("dz_1", com.nashi.artist.AudioSource.DEEZER, "1",
                        null, null, "T", 30, "a", null))).build());
        artists.save(MapFixtures.withAudio(MapFixtures.artist("upload", Region.LVIV, LVIV_ID, "Львів")).build());
        MapService deezerOff = new MapService(mongo, regionCatalog, placeStore, fileStorage,
                com.nashi.audio.AudioPolicy.of(false, true, true));

        var byName = deezerOff.snapshot().pins().stream()
                .collect(java.util.stream.Collectors.toMap(MapPin::name, MapPin::hasAudio));

        assertThat(byName).containsEntry("Гурт deezer-only", false).containsEntry("Гурт upload", true);
    }

    @Test
    void versionChangesWithTheEnabledAudioSourcesOnTheSameData() {
        MapService allOn = new MapService(mongo, regionCatalog, placeStore, fileStorage,
                com.nashi.audio.AudioPolicy.of(true, true, true));
        MapService allOnAgain = new MapService(mongo, regionCatalog, placeStore, fileStorage,
                com.nashi.audio.AudioPolicy.of(true, true, true));
        MapService deezerOff = new MapService(mongo, regionCatalog, placeStore, fileStorage,
                com.nashi.audio.AudioPolicy.of(false, true, true));

        // a provider switch changes hasAudio on pins without touching any artist, so it must change the ETag
        assertThat(allOnAgain.snapshot().version()).isEqualTo(allOn.snapshot().version());
        assertThat(deezerOff.snapshot().version()).isNotEqualTo(allOn.snapshot().version());
    }

    @Test
    void versionCoversTheResponseShape() {
        assertThat(MapService.versionSeed(com.nashi.audio.AudioPolicy.of(false, false, true)))
                .startsWith(MapService.SHAPE + "|")
                .contains("UPLOAD", "COMMONS")
                .doesNotContain("DEEZER", "ITUNES");
    }
}
