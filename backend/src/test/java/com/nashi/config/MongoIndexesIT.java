package com.nashi.config;

import com.nashi.support.MongoIT;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link MongoIndexes} runs on startup of the shared test context. */
class MongoIndexesIT extends MongoIT {

    @Autowired
    private MongoTemplate mongo;

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
}
