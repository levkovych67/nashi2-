package com.nashi.health;

import com.nashi.support.MongoIT;
import com.nashi.support.OpenApiContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

class HealthControllerIT extends MongoIT {

    @Autowired
    private RestTestClient http;

    @Test
    void upAgainstRealMongo() {
        http.get().uri("/api/v2/health").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT);
        OpenApiContract.assertResponse("getHealth", http.get().uri("/api/v2/health").exchange()
                .returnResult(byte[].class));
    }

    @Test
    void unknownApiPathIsProblem404() {
        http.get().uri("/api/v2/no-such-endpoint").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("about:blank")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.title").isEqualTo("Не знайдено")
                .jsonPath("$.instance").isEqualTo("/api/v2/no-such-endpoint");
    }

    @Test
    void apiDocsAreServedInTestProfile() {
        http.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.paths['/api/v2/health'].get").exists();
    }

    @Test
    void corsPreflightFromConfiguredOrigin() {
        http.method(HttpMethod.OPTIONS).uri("/api/v2/health")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
    }
}
