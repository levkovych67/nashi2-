package com.nashi.config;

import com.nashi.support.MongoIT;
import com.nashi.support.OpenApiContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class PublicConfigControllerIT extends MongoIT {

    @Autowired
    private RestTestClient http;

    @Test
    void returnsDefaultsFromProperties() {
        http.get().uri("/api/v2/config/public").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.fragmentSeconds").isEqualTo(15)
                .jsonPath("$.map.regionMaxZoom").isEqualTo(7)
                .jsonPath("$.map.placeMaxZoom").isEqualTo(10)
                .jsonPath("$.map.minZoom").isEqualTo(5)
                .jsonPath("$.map.maxZoom").isEqualTo(17)
                .jsonPath("$.likes.batchMax").isEqualTo(50)
                .jsonPath("$.likes.flushMs").isEqualTo(600)
                .jsonPath("$.submission.maxImages").isEqualTo(5)
                .jsonPath("$.submission.maxImageMb").isEqualTo(10)
                .jsonPath("$.submission.summaryMin").isEqualTo(30)
                .jsonPath("$.submission.summaryMax").isEqualTo(1000)
                .jsonPath("$.turnstileSiteKey").value(v -> assertThat(v).isNull());
        OpenApiContract.assertResponse("getPublicConfig",
                http.get().uri("/api/v2/config/public").exchange().returnResult(byte[].class));
        // present as an explicit null (required, nullable in the contract), not omitted
        assertThat(http.get().uri("/api/v2/config/public").exchange().returnResult(String.class).getResponseBody())
                .contains("\"turnstileSiteKey\":null");
    }
}
