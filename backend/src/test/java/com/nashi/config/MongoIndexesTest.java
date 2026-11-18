package com.nashi.config;

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoIndexesTest {

    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> collection = mock(MongoCollection.class);

    MongoIndexesTest() {
        when(collection.getNamespace()).thenReturn(new MongoNamespace("nashi_v2", "places"));
        when(collection.createIndex(any(Bson.class), any(IndexOptions.class)))
                .thenThrow(new IllegalStateException("IndexOptionsConflict"));
    }

    @Test
    void anOrdinaryIndexThatFailsOnlyWarns() {
        assertThatCode(() -> MongoIndexes.ensure(collection, Indexes.ascending("region"),
                new IndexOptions().name("region_1"))).doesNotThrowAnyException();
    }

    @Test
    void aUniqueIndexThatFailsStopsTheStartup() {
        assertThatThrownBy(() -> MongoIndexes.ensure(collection, Indexes.ascending("slug"),
                new IndexOptions().name("slug_unique").unique(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("places.slug_unique");
    }
}
