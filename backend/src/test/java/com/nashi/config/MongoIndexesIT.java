package com.nashi.config;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.support.MongoIT;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link MongoIndexes} runs on startup of the shared test context. */
class MongoIndexesIT extends MongoIT {

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ArtistRepository artists;

    private Map<String, Document> indexes(String collection) {
        List<Document> list = mongo.getCollection(collection).listIndexes().into(new ArrayList<>());
        return list.stream().collect(Collectors.toMap(d -> d.getString("name"), d -> d.get("key", Document.class)));
    }

    @Test
    void createsPlacesIndexes() {
        Map<String, Document> places = indexes("places");

        assertThat(places).containsKeys("nameNormalized_1_population_-1", "region_1", "point_2dsphere");
        assertThat(places.get("nameNormalized_1_population_-1"))
                .isEqualTo(new Document("nameNormalized", 1).append("population", -1));
        assertThat(places.get("point_2dsphere")).isEqualTo(new Document("point", "2dsphere"));
    }

    @Test
    void runningAgainIsHarmless() {
        new MongoIndexes(mongo).run(null);

        assertThat(indexes("places")).containsKeys("nameNormalized_1_population_-1", "region_1", "point_2dsphere");
    }

    @Test
    void createsAllIndexes() {
        Map<String, Document> artistIndexes = indexes("artists");

        assertThat(artistIndexes).containsKeys("slug_unique", "wikidataId_unique_sparse", "legacyV1Id_unique_sparse",
                "pin_2dsphere", "status_1_origin.region_1", "status_1_nameNormalized_1", "status_1_likesCount_-1",
                "status_1_origin.placeId_1");
        assertThat(artistIndexes.get("pin_2dsphere")).isEqualTo(new Document("pin", "2dsphere"));
        assertThat(artistIndexes.get("status_1_likesCount_-1"))
                .isEqualTo(new Document("status", 1).append("likesCount", -1));
        assertThat(isUnique("artists", "slug_unique")).isTrue();
        assertThat(isUnique("artists", "wikidataId_unique_sparse")).isTrue();
        assertThat(isUnique("artists", "legacyV1Id_unique_sparse")).isTrue();
        assertThat(indexes("submissions").get("status_1_createdAt_-1"))
                .isEqualTo(new Document("status", 1).append("createdAt", -1));
        assertThat(indexes("jobs_runs").get("job_1_startedAt_-1"))
                .isEqualTo(new Document("job", 1).append("startedAt", -1));
    }

    @Test
    void duplicateSlugIsRejected() {
        String slug = "dup-" + UUID.randomUUID();
        Artist first = Artist.builder().slug(slug).name("Перший").build();
        Artist second = Artist.builder().slug(slug).name("Другий").build();
        try {
            artists.save(first);

            assertThatThrownBy(() -> artists.save(second)).isInstanceOf(DuplicateKeyException.class);
        } finally {
            artists.deleteById(first.getId());
        }
    }

    private boolean isUnique(String collection, String index) {
        return mongo.getCollection(collection).listIndexes().into(new ArrayList<>()).stream()
                .filter(d -> index.equals(d.getString("name"))).findFirst().orElseThrow()
                .getBoolean("unique", false);
    }
}
