package com.nashi.like;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.geo.Region;
import com.nashi.map.MapFixtures;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LikeControllerIT extends MongoIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The limiter is a singleton shared by every IT, so each request gets its own client IP. */
    private static final AtomicInteger IP_SEQ = new AtomicInteger();

    @LocalServerPort
    private int port;
    private RestTestClient http;
    @Autowired
    private ArtistRepository artists;

    private Artist artist;

    @BeforeEach
    void seed() {
        // The autoconfigured client (Apache HC5) silently re-sends a 429 after its Retry-After; the JDK client does not.
        http = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port).build();
        artists.deleteAll();
        artist = artists.save(MapFixtures.artist("likes", Region.LVIV, MapFixtures.LVIV_ID, "Львів").build());
    }

    private static String freshIp() {
        int n = IP_SEQ.incrementAndGet();
        return "10.12." + (n / 250) + "." + (n % 250 + 1);
    }

    private EntityExchangeResult<byte[]> post(String id, String ip, String body) {
        return http.post().uri("/api/v2/musicians/" + id + "/likes")
                .header("X-Forwarded-For", ip)
                .header("Content-Type", "application/json")
                .body(body)
                .exchange().returnResult(byte[].class);
    }

    @Test
    void responseHasNewTotal() throws Exception {
        var res = post(artist.getId(), freshIp(), "{\"count\":3}");
        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(res.getResponseBody()).get("likesCount").asLong()).isEqualTo(3);
        res = post(artist.getId(), freshIp(), "{\"count\":50}");
        assertThat(JSON.readTree(res.getResponseBody()).get("likesCount").asLong()).isEqualTo(53);
    }

    @Test
    void zeroOrFiftyOneIs400() throws Exception {
        for (String body : new String[] {"{\"count\":0}", "{\"count\":51}", "{}"}) {
            var res = post(artist.getId(), freshIp(), body);
            assertThat(res.getStatus()).as(body).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode json = JSON.readTree(res.getResponseBody());
            assertThat(json.get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(json.get("errors").get(0).get("field").asString()).as(body).isEqualTo("count");
        }
    }

    @Test
    void unknownOrHiddenIs404() throws Exception {
        Artist hidden = artists.save(MapFixtures.artist("hid", Region.LVIV, MapFixtures.LVIV_ID, "Львів")
                .status(ArtistStatus.HIDDEN).build());
        for (String id : new String[] {hidden.getId(), "missing"}) {
            var res = post(id, freshIp(), "{\"count\":1}");
            assertThat(res.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(JSON.readTree(res.getResponseBody()).get("code").asString()).isEqualTo("NOT_FOUND");
        }
    }

    @Test
    void twentyFirstRequestIs429WithRetryAfter() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 20; i++) {
            assertThat(post(artist.getId(), ip, "{\"count\":1}").getStatus()).isEqualTo(HttpStatus.OK);
        }
        var res = post(artist.getId(), ip, "{\"count\":1}");
        assertThat(res.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(res.getResponseHeaders().getFirst("Retry-After"))).isBetween(1L, 10L);
        assertThat(JSON.readTree(res.getResponseBody()).get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(artists.findById(artist.getId()).orElseThrow().getLikesCount()).isEqualTo(20);
    }
}
