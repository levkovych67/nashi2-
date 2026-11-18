package com.nashi.ratelimit;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.geo.Region;
import com.nashi.map.MapFixtures;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which address the rate limits key on, through the real Tomcat: requests come from 127.0.0.1 (an internal proxy), so
 * the key is the rightmost {@code X-Forwarded-For} hop that is not an internal proxy. A client-supplied
 * {@code Forwarded} header or extra leftmost {@code X-Forwarded-For} values must not choose it.
 */
class ClientIpIT extends MongoIT {

    @LocalServerPort
    private int port;
    @Autowired
    private ArtistRepository artists;

    private RestTestClient http;
    private Artist artist;

    @BeforeEach
    void seed() {
        // JDK client: the autoconfigured one (Apache HC5) silently re-sends a 429 after its Retry-After.
        http = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://127.0.0.1:" + port).build();
        artists.deleteAll();
        artist = artists.save(MapFixtures.artist("client-ip", Region.LVIV, MapFixtures.LVIV_ID, "Львів").build());
    }

    private HttpStatus like(String forwarded, String xff) {
        var spec = http.post().uri("/api/v2/musicians/" + artist.getId() + "/likes")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", xff);
        if (forwarded != null) {
            spec = spec.header("Forwarded", forwarded);
        }
        return HttpStatus.valueOf(spec.body("{\"count\":1}").exchange().returnResult(byte[].class)
                .getStatus().value());
    }

    @Test
    void keyIsTheRightmostUntrustedHopNotForwardedOrTheLeftmostValue() {
        int capacity = RateLimitPolicy.LIKES.capacity();
        // Every request spoofs a different Forwarded and leftmost value; only the hop nginx appended stays the same.
        for (int i = 0; i < capacity; i++) {
            assertThat(like("for=6.6.6." + i, "6.6.6." + i + ", 198.51.100.7")).as("request %d", i)
                    .isEqualTo(HttpStatus.OK);
        }
        assertThat(like("for=6.6.6.6", "6.6.6.6, 198.51.100.7")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // The same bucket without any spoofing: the key is exactly 198.51.100.7
        assertThat(like(null, "198.51.100.7")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Another client behind the same spoofed values has its own bucket
        assertThat(like("for=6.6.6.6", "6.6.6.6, 198.51.100.8")).isEqualTo(HttpStatus.OK);
    }
}
