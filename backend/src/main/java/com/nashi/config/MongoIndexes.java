package com.nashi.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the MongoDB indexes on startup, before any other runner (Spring Data's auto-index creation is off). The
 * names and keys follow {@code docs/02-contracts.md} section 3; {@code places.point_2dsphere} is internal (needed by
 * the nearest-place lookup). Each index is tried on its own: a unique index that cannot be created stops the startup
 * (the data would silently allow duplicates); any other failure is logged and the remaining indexes are still
 * created.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MongoIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexes.class);

    private final MongoTemplate mongo;

    public MongoIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        MongoCollection<Document> places = mongo.getCollection("places");
        ensure(places, Indexes.compoundIndex(Indexes.ascending("nameNormalized"), Indexes.descending("population")),
                new IndexOptions().name("nameNormalized_1_population_-1"));
        ensure(places, Indexes.ascending("region"), new IndexOptions().name("region_1"));
        ensure(places, Indexes.geo2dsphere("point"), new IndexOptions().name("point_2dsphere"));
    }

    static void ensure(MongoCollection<Document> collection, Bson keys, IndexOptions options) {
        String where = collection.getNamespace().getCollectionName() + "." + options.getName();
        try {
            collection.createIndex(keys, options);
            log.debug("Ensured index {}", where);
        } catch (RuntimeException e) {
            if (options.isUnique()) {
                throw new IllegalStateException("Cannot create unique index " + where, e);
            }
            log.warn("Could not create index {}: {}", where, e.getMessage());
        }
    }
}
