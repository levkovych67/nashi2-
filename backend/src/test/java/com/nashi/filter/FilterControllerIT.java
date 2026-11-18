package com.nashi.filter;

import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import com.nashi.support.OpenApiContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import static com.nashi.map.MapFixtures.LVIV_ID;
import static com.nashi.map.MapFixtures.artist;
import static org.assertj.core.api.Assertions.assertThat;

class FilterControllerIT extends MongoIT {

    @Autowired
    private RestTestClient http;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private ApplicationEventPublisher events;

    @BeforeEach
    void seed() {
        artists.deleteAll();
        artists.save(artist("a", Region.LVIV, LVIV_ID, "Львів").build());
        artists.save(artist("b", Region.LVIV, LVIV_ID, "Львів").build());
        events.publishEvent(new ArtistChangedEvent("any"));
    }

    @AfterEach
    void clear() {
        artists.deleteAll();
        events.publishEvent(new ArtistChangedEvent("any"));
    }

    @Test
    void returnsRegionFilterWithEtagAnd304() {
        var res = http.get().uri("/api/v2/filters").exchange().returnResult(String.class);
        assertThat(res.getStatus()).isEqualTo(HttpStatus.OK);
        OpenApiContract.assertResponse("getFilters",
                http.get().uri("/api/v2/filters").exchange().returnResult(byte[].class));
        String etag = res.getResponseHeaders().getFirst("ETag");
        assertThat(etag).matches("\"[0-9a-f]{8}\"");
        assertThat(res.getResponseHeaders().getFirst("Cache-Control")).isEqualTo("public, max-age=60");
        assertThat(res.getResponseBody()).contains("\"key\":\"region\"", "\"MULTI_SELECT\"", "\"value\":\"LVIV\"",
                "\"count\":2");

        var cached = http.get().uri("/api/v2/filters").header("If-None-Match", etag).exchange()
                .returnResult(String.class);
        assertThat(cached.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED);
        OpenApiContract.assertResponse("getFilters", http.get().uri("/api/v2/filters").header("If-None-Match", etag)
                .exchange().returnResult(byte[].class));
        assertThat(cached.getResponseBody()).isNullOrEmpty();
        assertThat(cached.getResponseHeaders().getFirst("ETag")).isEqualTo(etag);
        assertThat(cached.getResponseHeaders().getFirst("Cache-Control")).isEqualTo("public, max-age=60");
    }
}
