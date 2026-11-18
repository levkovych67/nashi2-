package com.nashi.place;

import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSearchServiceIT extends MongoIT {

    @Autowired
    private PlaceSearchService search;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void seed() {
        PlaceFixtures.clear(mongo);
        PlaceFixtures.insertAll(mongo);
    }

    @AfterEach
    void clear() {
        PlaceFixtures.clear(mongo);
    }

    private List<String> ids(String q) {
        return ids(q, null, 10);
    }

    private List<String> ids(String q, Region region, int limit) {
        return search.search(q, region, limit).stream().map(PlaceSuggestion::placeId).toList();
    }

    @Test
    void prefixOfAnyWordMatches() {
        assertThat(ids("под")).containsExactly("Q193965", "Q4368083");
        assertThat(ids("кам'ян")).containsExactly("Q193965");
        assertThat(ids("камян")).containsExactly("Q193965");
        assertThat(ids("Кам’янець-под")).containsExactly("Q193965");
    }

    @Test
    void doesNotMatchInsideAWord() {
        assertThat(ids("ьвів")).isEmpty();
        assertThat(ids("одільськ")).isEmpty();
    }

    @Test
    void ignoresCaseApostrophesAndStress() {
        assertThat(ids("БРОДИ")).containsExactly("Q465104");
        assertThat(ids("бро́ди")).containsExactly("Q465104");
    }

    @Test
    void sortsByPopulationDesc() {
        assertThat(ids("ки")).containsExactly("Q1899", "Q4220129");
    }

    @Test
    void placesWithoutPopulationComeLast() {
        assertThat(ids("по")).containsExactly("Q193965", "Q4368083");
    }

    @Test
    void filtersByRegion() {
        assertThat(ids("к", null, 10)).hasSize(3);
        assertThat(ids("к", Region.KHMELNYTSKYI, 10)).containsExactly("Q193965");
        assertThat(ids("к", Region.MYKOLAIV, 10)).containsExactly("Q4220129");
        assertThat(ids("л", Region.KYIV_CITY, 10)).isEmpty();
    }

    @Test
    void limitsResults() {
        assertThat(ids("к", null, 2)).containsExactly("Q1899", "Q193965");
    }

    @Test
    void treatsRegexCharactersLiterally() {
        assertThat(ids(".*")).isEmpty();
        assertThat(ids("(к")).isEmpty();
    }

    @Test
    void returnsTheSuggestionShapeOfTheContract() {
        assertThat(search.search("бро", null, 10)).containsExactly(new PlaceSuggestion("Q465104", "Броди",
                PlaceType.CITY, Region.LVIV, "Львівська область", 50.078055555, 25.154166666));
    }
}
