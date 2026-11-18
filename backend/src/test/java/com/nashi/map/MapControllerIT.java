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
import com.nashi.artist.SlugAllocator;
import org.bson.types.ObjectId;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private static final int PINS_GZIP_BUDGET_BYTES = 225 * 1024;
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
     * NFR-02 target is 150 KB; unreachable with the contract 2.1 field set, see docs/adr/0003-map-pins-payload.md.
     * This guards against regressions until the decision is made. Synthetic but production-shaped data, seeded.
     */
    @Test
    void pinsPayloadWithinBudgetFor5000() throws IOException {
        artists.deleteAll();
        Random rnd = new Random(42);
        String[] firstNames = vocabulary(rnd, 150, 3, 4);
        String[] surnames = vocabulary(rnd, 300, 3, 5);
        String[] suffix = {"бенд", "Оркестр", "Квартет", "Тріо", "Бригада"};
        int cities = 1500;
        double[] cityLat = new double[cities];
        double[] cityLon = new double[cities];
        Region[] cityRegion = new Region[cities];
        Region[] allRegions = Region.values();
        for (int c = 0; c < cities; c++) {
            cityLat[c] = 45.5 + rnd.nextDouble() * 5;
            cityLon[c] = 23 + rnd.nextDouble() * 16;
            cityRegion[c] = allRegions[rnd.nextInt(allRegions.length)];
        }
        Set<String> slugs = new java.util.HashSet<>();
        List<Artist> batch = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) {
            ObjectId id = new ObjectId();
            boolean band = rnd.nextDouble() < 0.3;
            String name = !band ? firstNames[rnd.nextInt(150)] + " " + surnames[rnd.nextInt(300)]
                    : rnd.nextBoolean() ? "Гурт " + surnames[rnd.nextInt(300)]
                    : surnames[rnd.nextInt(300)] + " " + suffix[rnd.nextInt(suffix.length)];
            // 20% of artists in the 10 big cities, the rest spread over all 1500 places
            int city = rnd.nextDouble() < 0.2 ? rnd.nextInt(10) : rnd.nextInt(cities);
            double offset = 0.02;
            batch.add(Artist.builder().id(id.toHexString()).slug(SlugAllocator.allocate(name, slugs::contains))
                    .name(name).kind(band ? MusicianKind.BAND : MusicianKind.PERSON)
                    .status(ArtistStatus.PUBLISHED).summary("Опис")
                    .origin(new Origin("Q" + (10_000 + city), "Місто", cityRegion[city], OriginBasis.FORMATION,
                            PinPrecision.CITY, new GeoJsonPoint(cityLon[city], cityLat[city])))
                    .pin(new GeoJsonPoint(cityLon[city] + (rnd.nextDouble() - 0.5) * offset,
                            cityLat[city] + (rnd.nextDouble() - 0.5) * offset))
                    .images(rnd.nextDouble() < 0.7 ? List.of(new ImageAsset(null, null,
                            "artists/" + id.toHexString() + "/0-thumb.jpg", null, null, null)) : List.of())
                    .audio(rnd.nextDouble() < 0.4 ? List.of(new AudioClip("c", AudioSource.UPLOAD, null, "k", "u", "T",
                            10, "a", null)) : List.of())
                    .build());
            slugs.add(batch.get(i).getSlug());
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
        assertThat(gz.size()).isLessThanOrEqualTo(PINS_GZIP_BUDGET_BYTES);
    }

    /** Pronounceable pseudo-Ukrainian words, capitalized. */
    private static String[] vocabulary(Random rnd, int size, int minSyl, int maxSyl) {
        String[] cons = {"б", "в", "г", "д", "з", "к", "л", "м", "н", "п", "р", "с", "т", "х", "ч", "ш", "ж", "ц"};
        String[] vow = {"а", "е", "и", "і", "о", "у", "я", "ю"};
        Set<String> words = new java.util.LinkedHashSet<>();
        while (words.size() < size) {
            StringBuilder w = new StringBuilder();
            int syl = minSyl + rnd.nextInt(maxSyl - minSyl + 1);
            for (int s = 0; s < syl; s++) {
                w.append(cons[rnd.nextInt(cons.length)]).append(vow[rnd.nextInt(vow.length)]);
            }
            words.add(Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return words.toArray(new String[0]);
    }
}
