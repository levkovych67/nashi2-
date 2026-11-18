package com.nashi.place;

import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.ArrayList;
import java.util.List;

class PlaceControllerIT extends MongoIT {

    @Autowired
    private RestTestClient http;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void seed() {
        PlaceFixtures.clear(mongo);
    }

    @AfterEach
    void clear() {
        PlaceFixtures.clear(mongo);
    }

    private RestTestClient.ResponseSpec search(String query) {
        return http.get().uri("/api/v2/places/search?" + query).exchange();
    }

    private void expectValidationFailed(String query, String field) {
        search(query)
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void answersSuggestionsOfTheContractShape() {
        PlaceFixtures.insertAll(mongo);

        search("q=бро")
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        [{"placeId":"Q465104","name":"Броди","type":"CITY","region":"LVIV",
                          "regionName":"Львівська область","lat":50.078055555,"lon":25.154166666}]""",
                        JsonCompareMode.STRICT);
    }

    @Test
    void filtersByRegionParameter() {
        PlaceFixtures.insertAll(mongo);

        search("q=ка&region=KHMELNYTSKYI")
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].placeId").isEqualTo(List.of("Q193965"));
    }

    @Test
    void shortQueryIs400() {
        expectValidationFailed("q=к", "q");
    }

    @Test
    void queryOfApostrophesAndSpacesOnlyIs400() {
        expectValidationFailed("q=’ '", "q");
    }

    @Test
    void missingQueryIs400() {
        expectValidationFailed("", "q");
    }

    @Test
    void limitOutsideOneToTwentyIs400() {
        expectValidationFailed("q=ки&limit=0", "limit");
        expectValidationFailed("q=ки&limit=21", "limit");
        expectValidationFailed("q=ки&limit=x", "limit");
    }

    @Test
    void unknownRegionIs400() {
        expectValidationFailed("q=ки&region=LEMBERG", "region");
    }

    @Test
    void defaultLimitIsTen() {
        List<Place> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(PlaceFixtures.place("Q90000" + i, "Тестове " + i, PlaceType.VILLAGE, Region.SUMY, 50.9, 34.8,
                    null, (long) i));
        }
        mongo.insertAll(many);

        search("q=тест").expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(10);
        search("q=тест&limit=20").expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(12);
        search("q=тест&limit=1").expectStatus().isOk().expectBody()
                .jsonPath("$[0].placeId").isEqualTo("Q9000011");
    }
}
