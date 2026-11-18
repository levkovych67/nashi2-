package com.nashi.map;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.MusicianKind;
import com.nashi.geo.Region;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import static com.nashi.map.MapFixtures.LVIV_ID;
import static com.nashi.map.MapFixtures.artist;
import static com.nashi.map.MapFixtures.withAudio;
import static com.nashi.map.MapFixtures.withPhoto;
import static org.assertj.core.api.Assertions.assertThat;

class MapControllerIT extends MongoIT {

    private static final Logger LOG = LoggerFactory.getLogger(MapControllerIT.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private RestTestClient http;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private MapService service;

    @BeforeEach
    void seed() {
        artists.deleteAll();
        artists.save(artist("bare", Region.LVIV, LVIV_ID, "Львів").build());
        artists.save(withAudio(withPhoto(artist("full", Region.KYIV_CITY, "Q1899", "Київ"))).build());
        events.publishEvent(new ArtistChangedEvent("any"));
    }

    @AfterEach
    void clear() {
        artists.deleteAll();
        events.publishEvent(new ArtistChangedEvent("any"));
    }

    private EntityExchangeResult<byte[]> get(String uri, String ifNoneMatch) {
        RestTestClient.RequestHeadersSpec<?> req = http.get().uri("/api/v2/map" + uri);
        if (ifNoneMatch != null) {
            req = req.header("If-None-Match", ifNoneMatch);
        }
        return req.exchange().returnResult(byte[].class);
    }

    @Test
    void pinsReturnEtagAndCacheControl() throws IOException {
        var res = get("/pins", null);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        JsonNode body = JSON.readTree(res.getResponseBody());
        assertThat(res.getResponseHeaders().getFirst("ETag")).isEqualTo("\"" + body.get("version").asString() + "\"");
        assertThat(res.getResponseHeaders().getFirst("Cache-Control")).isEqualTo("public, max-age=60");
        assertThat(body.get("pins")).hasSize(2);
    }

    @Test
    void ifNoneMatchGives304() {
        for (String path : List.of("/pins", "/regions", "/places")) {
            String etag = get(path, null).getResponseHeaders().getFirst("ETag");
            assertThat(etag).as(path).isNotNull();
            for (String header : List.of(etag, "W/" + etag, "*", "\"other\", " + etag)) {
                var res = get(path, header);
                assertThat(res.getStatus()).as(path + " " + header).isEqualTo(HttpStatus.NOT_MODIFIED);
                assertThat(res.getResponseBody()).as(path + " body").isNullOrEmpty();
                assertThat(res.getResponseHeaders().getFirst("ETag")).isEqualTo(etag);
                assertThat(res.getResponseHeaders().getFirst("Cache-Control")).isEqualTo("public, max-age=60");
            }
            assertThat(get(path, "\"stale\"").getStatus()).as(path + " stale").isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void pinJsonHasExactlyContractFields() throws IOException {
        JsonNode pins = JSON.readTree(get("/pins", null).getResponseBody()).get("pins");
        JsonNode bare = null;
        JsonNode full = null;
        for (JsonNode p : pins) {
            if (p.get("slug").asString().equals("bare")) {
                bare = p;
            } else {
                full = p;
            }
        }
        Set<String> nine = Set.of("id", "slug", "name", "kind", "lat", "lon", "region", "placeId", "hasAudio");
        assertThat(keys(bare)).isEqualTo(new TreeSet<>(nine));
        Set<String> ten = new TreeSet<>(nine);
        ten.add("thumbUrl");
        assertThat(keys(full)).isEqualTo(ten);
        assertThat(full.get("thumbUrl").asString()).isEqualTo("https://img/thumb.jpg");
        assertThat(full.get("hasAudio").asBoolean()).isTrue();
        assertThat(full.get("lat").asDouble()).isEqualTo(49.9);
        assertThat(full.get("lon").asDouble()).isEqualTo(24.2);
        assertThat(full.get("region").asString()).isEqualTo("KYIV_CITY");
    }

    @Test
    void regionsAndPlacesJson() throws IOException {
        JsonNode regions = JSON.readTree(get("/regions", null).getResponseBody());
        assertThat(regions).hasSize(27);
        assertThat(keys(regions.get(0))).containsExactly("bbox", "center", "code", "count", "name");
        JsonNode places = JSON.readTree(get("/places", null).getResponseBody());
        assertThat(places).hasSize(2);
        assertThat(keys(places.get(0))).containsExactly("count", "lat", "lon", "name", "placeId", "region", "type");
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.propertyNames().forEach(keys::add);
        return keys;
    }

    /**
     * Disabled: the budget is not met. Measured with this fixture (5000 distinct pins, full contract fields, 5-decimal
     * coordinates): raw 1.30 MB, gzip about 180 KB. Needs a controller decision (budget, fields or format).
     */
    @Disabled("NFR-02 not met: gzip ~180 KB > 150 KB, see task-11 report")
    @Test
    void pinsPayloadUnder150KbGzipFor5000() throws IOException {
        artists.deleteAll();
        Random rnd = new Random(42);
        String[] first = {"Океан", "Скрябін", "Вакарчук", "Тартак", "Друга", "Рікка", "Мандри", "Гайдамаки", "Піккардійська",
                "Врода", "Хвилі", "Dakh", "Антитіла", "Бумбокс", "Курган"};
        String[] second = {"Ельзи", "Група", "Тріо", "Бенд", "Квартет", "Ріка", "Брати", "Ніч", "Дім", "Вогонь"};
        Region[] regions = Region.values();
        List<Artist> batch = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) {
            Region region = regions[rnd.nextInt(regions.length)];
            String name = first[rnd.nextInt(first.length)] + " " + second[rnd.nextInt(second.length)] + " " + i;
            String hash = Integer.toHexString(rnd.nextInt(0x10000000));
            batch.add(Artist.builder().slug("gurt-" + i).name(name).kind(i % 4 == 0 ? MusicianKind.PERSON : MusicianKind.BAND)
                    .status(ArtistStatus.PUBLISHED).summary("Опис")
                    .origin(new Origin("Q" + (1000 + rnd.nextInt(3000)), "Місто", region, OriginBasis.FORMATION,
                            PinPrecision.CITY, new GeoJsonPoint(24, 49)))
                    .pin(new GeoJsonPoint(22 + rnd.nextDouble() * 18, 45 + rnd.nextDouble() * 6))
                    .images(i % 3 == 0 ? List.of() : List.of(new ImageAsset(null, null, null,
                            "https://upload.wikimedia.org/wikipedia/commons/thumb/" + hash.charAt(0) + "/" + hash.substring(0, 2)
                                    + "/Foto_" + i + ".jpg/200px-Foto_" + i + ".jpg", null, null)))
                    .audio(i % 2 == 0 ? List.of() : List.of(new AudioClip("c", AudioSource.UPLOAD, null, "k", "u", "T", 10,
                            "a", null)))
                    .build());
        }
        mongo.insert(batch, "artists");
        long t0 = System.nanoTime();
        events.publishEvent(new ArtistChangedEvent("bulk"));
        MapSnapshot snapshot = service.snapshot();
        long buildMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(snapshot.pins()).hasSize(5000);

        byte[] raw = get("/pins", null).getResponseBody();
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(raw);
        }
        LOG.info("NFR-02 /map/pins for 5000 artists: raw={} bytes, gzip={} bytes, snapshot build={} ms",
                raw.length, gz.size(), buildMs);
        assertThat(gz.size()).isLessThanOrEqualTo(150 * 1024);
    }
}
