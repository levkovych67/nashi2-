package com.nashi.submission;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.geo.Region;
import com.nashi.map.MapFixtures;
import com.nashi.media.FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceRepository;
import com.nashi.place.PlaceType;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionControllerIT extends MongoIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The rate limiter is a singleton shared by every IT: each request gets its own client IP. */
    private static final AtomicInteger IP_SEQ = new AtomicInteger();

    @LocalServerPort
    private int port;
    private RestTestClient http;
    @Autowired
    private SubmissionRepository submissions;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private PlaceRepository places;
    @Autowired
    private FileStorage storage;
    @Autowired
    private IpHasher ipHasher;
    @Autowired
    private RecordedEvents events;

    private final List<String> createdKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // The autoconfigured client (Apache HC5) silently re-sends a 429 after its Retry-After; the JDK client does not.
        http = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port).build();
        events.clear();
        submissions.deleteAll();
        artists.deleteAll();
        places.save(new Place("Q36036", "Львів", "львів", PlaceType.CITY, Region.LVIV,
                new GeoJsonPoint(24.03, 49.84), null, null));
    }

    @AfterEach
    void removeUploads() {
        submissions.findAll().forEach(s -> {
            if (s.getImageKeys() != null) {
                s.getImageKeys().forEach(storage::delete);
            }
        });
        createdKeys.forEach(storage::delete);
    }

    private static String freshIp() {
        int n = IP_SEQ.incrementAndGet();
        return "10.14." + (n / 250) + "." + (n % 250 + 1);
    }

    private static byte[] image(String name) throws IOException, URISyntaxException {
        return Files.readAllBytes(Path.of(SubmissionControllerIT.class.getResource("/images/" + name).toURI()));
    }

    private static String json(String name, String fax) {
        return """
                {"name": "%s", "kind": "BAND", "originPlaceId": "Q36036", "summary": "%s",
                 "genres": ["інді", "фолк"], "links": ["https://open.spotify.com/artist/abc"],
                 "youtubeChannelUrl": "https://www.youtube.com/@band", "contact": "telegram @nick",
                 "consent": true, "faxNumber": "%s"}
                """.formatted(name, SubmissionRequests.SUMMARY, fax);
    }

    private EntityExchangeResult<byte[]> post(String ip, String data, byte[]... images) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("data", data, MediaType.APPLICATION_JSON);
        int n = 0;
        for (byte[] bytes : images) {
            int index = n++;
            body.part("images", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return "photo" + index + ".jpg";
                }
            });
        }
        return http.post().uri("/api/v2/submissions")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .exchange().returnResult(byte[].class);
    }

    private static JsonNode body(EntityExchangeResult<byte[]> res) {
        return JSON.readTree(res.getResponseBody());
    }

    @Test
    void acceptsValidSubmissionAndStoresProcessedImages() throws Exception {
        var res = post(freshIp(), json("Океан", ""), image("tiny.jpg"), image("photo.png"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        String id = body(res).get("id").asString();
        assertThat(body(res).get("status").asString()).isEqualTo("PENDING");

        Submission saved = submissions.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(saved.getName()).isEqualTo("Океан");
        assertThat(saved.getConsentAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getModerationLog()).isEmpty();
        assertThat(saved.getImageKeys()).containsExactly(
                "submissions/" + id + "/0.jpg", "submissions/" + id + "/0-thumb.jpg",
                "submissions/" + id + "/1.jpg", "submissions/" + id + "/1-thumb.jpg");
        for (String key : saved.getImageKeys()) {
            byte[] stored = storage.get(key);
            assertThat(stored).as(key).startsWith((byte) 0xFF, (byte) 0xD8);
        }
        assertThat(events.submissionCreated().stream()).extracting(SubmissionCreatedEvent::submissionId)
                .containsExactly(id);
    }

    @Test
    void rejectsSixthImage() throws Exception {
        byte[] jpg = image("tiny.jpg");
        var res = post(freshIp(), json("Шість", ""), jpg, jpg, jpg, jpg, jpg, jpg);

        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body(res).get("errors").get(0).get("field").asString()).isEqualTo("images");
        assertThat(submissions.count()).isZero();
    }

    @Test
    void reportsAllInvalidFieldsInOneResponse() throws Exception {
        String data = """
                {"name": "Х", "kind": "BAND", "originPlaceId": "Q0", "summary": "коротко",
                 "links": ["javascript:alert(1)"], "consent": false}
                """;
        var res = post(freshIp(), data, image("tiny.jpg"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<String> fields = body(res).get("errors").valueStream().map(e -> e.get("field").asString()).toList();
        assertThat(fields).contains("name", "originPlaceId", "summary", "links[0]", "consent");
    }

    @Test
    void malformedDataIs400() throws Exception {
        var res = post(freshIp(), "{not json", image("tiny.jpg"));
        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).get("code").asString()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void renamedPdfIs415() throws Exception {
        var res = post(freshIp(), json("Фейк", ""), image("fake.jpg"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(body(res).get("code").asString()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(body(res).get("errors").get(0).get("field").asString()).isEqualTo("images[0]");
        assertThat(submissions.count()).isZero();
    }

    @Test
    void unsupportedSecondPhotoNamesItsIndex() throws Exception {
        var res = post(freshIp(), json("Фейк2", ""), image("tiny.jpg"), image("fake.jpg"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(body(res).get("errors").get(0).get("field").asString()).isEqualTo("images[1]");
        assertThat(submissions.count()).isZero();
    }

    @Test
    void fileBetween10And12MbIsReportedByIndex() throws Exception {
        var res = post(freshIp(), json("Завеликий", ""), new byte[11 * 1024 * 1024]);

        assertThat(res.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(res).get("errors").get(0).get("field").asString()).isEqualTo("images[0]");
    }

    @Test
    void honeypotIsSilentlyDiscarded() throws Exception {
        var res = post(freshIp(), json("Бот", "x"), image("tiny.jpg"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        String id = body(res).get("id").asString();
        assertThat(body(res).get("status").asString()).isEqualTo("PENDING");
        Submission saved = submissions.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.DISCARDED);
        assertThat(saved.getImageKeys()).isNullOrEmpty();
        assertThatThrownBy(() -> storage.get("submissions/" + id + "/0.jpg")).isInstanceOf(NoSuchKeyException.class);
        assertThat(events.submissionCreated()).isEmpty();
    }

    @Test
    void sixthSubmissionPerHourIs429() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            assertThat(post(ip, json("Гурт " + i, ""), image("tiny.jpg")).getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        }
        var res = post(ip, json("Гурт 6", ""), image("tiny.jpg"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body(res).get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(res.getResponseHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(submissions.count()).isEqualTo(5);
    }

    @Test
    void duplicateNameIsFlagged() throws Exception {
        Artist ocean = artists.save(MapFixtures.artist("ocean", Region.LVIV, MapFixtures.LVIV_ID, "Львів")
                .name("Океан Ельзи").status(ArtistStatus.PUBLISHED).build());

        var res = post(freshIp(), json("\\\"океан  ельзи\\\"", ""), image("tiny.jpg"));

        assertThat(res.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        Submission saved = submissions.findById(body(res).get("id").asString()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(saved.getDuplicateOfArtistId()).isEqualTo(ocean.getId());
    }

    @Test
    void storesIpHashNotIp() throws Exception {
        String ip = freshIp();
        var res = post(ip, json("Хеш", ""), image("tiny.jpg"));

        Submission saved = submissions.findById(body(res).get("id").asString()).orElseThrow();
        assertThat(saved.getIpHash()).isEqualTo(ipHasher.hash(ip)).doesNotContain(ip);
        assertThat(saved.toString()).doesNotContain(ip);
    }
}
