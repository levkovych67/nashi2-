package com.nashi.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoIndexConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsEveryIndexEvenWhenOneFails() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoCollection<Document> artists = mock(MongoCollection.class);
        when(mongoTemplate.getCollection("artists")).thenReturn(artists);
        // The first index fails (e.g. it already exists under another name); the rest must still be created.
        when(artists.createIndex(any(Bson.class), any(IndexOptions.class)))
                .thenThrow(new IllegalStateException("Index already exists with a different name"))
                .thenReturn("ok");

        new MongoIndexConfig(mongoTemplate).ensureIndexes().run();

        ArgumentCaptor<Bson> keys = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(artists, atLeastOnce()).createIndex(keys.capture(), options.capture());

        Map<String, String> byName = new LinkedHashMap<>();
        for (int i = 0; i < options.getAllValues().size(); i++) {
            byName.put(options.getAllValues().get(i).getName(), keys.getAllValues().get(i).toBsonDocument().toJson());
        }
        assertEquals(List.of("location_2dsphere", "wikidataId_unique", "slug_unique", "status_1_region_1", "categories_1"),
                List.copyOf(byName.keySet()));
        assertEquals(BsonDocument.parse("{status: 1, region: 1}").toJson(), byName.get("status_1_region_1"));
        assertEquals(BsonDocument.parse("{slug: 1}").toJson(), byName.get("slug_unique"));
        assertEquals(BsonDocument.parse("{categories: 1}").toJson(), byName.get("categories_1"));

        IndexOptions slug = options.getAllValues().get(2);
        assertTrue(slug.isUnique());
        assertFalse(options.getAllValues().get(3).isUnique());
    }
}
