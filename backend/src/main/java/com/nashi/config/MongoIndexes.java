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
 * names and keys follow {@code docs/02-contracts.md} section 3; {@code places.point_2dsphere} (the nearest-place
 * lookup) and {@code artists.legacyV1Id_unique_sparse} (the V1 migration) are internal. Each index is tried on its own: a unique index that cannot be created stops the startup
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
        MongoCollection<Document> artists = mongo.getCollection("artists");
        ensure(artists, Indexes.ascending("slug"), new IndexOptions().name("slug_unique").unique(true));
        ensure(artists, Indexes.ascending("wikidataId"),
                new IndexOptions().name("wikidataId_unique_sparse").unique(true).sparse(true));
        ensure(artists, Indexes.ascending("legacyV1Id"),
                new IndexOptions().name("legacyV1Id_unique_sparse").unique(true).sparse(true));
        ensure(artists, Indexes.geo2dsphere("pin"), new IndexOptions().name("pin_2dsphere"));
        ensure(artists, Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("origin.region")),
                new IndexOptions().name("status_1_origin.region_1"));
        ensure(artists, Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("nameNormalized")),
                new IndexOptions().name("status_1_nameNormalized_1"));
        ensure(artists, Indexes.compoundIndex(Indexes.ascending("status"), Indexes.descending("likesCount")),
                new IndexOptions().name("status_1_likesCount_-1"));
        ensure(artists, Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("origin.placeId")),
                new IndexOptions().name("status_1_origin.placeId_1"));

        MongoCollection<Document> places = mongo.getCollection("places");
        ensure(places, Indexes.compoundIndex(Indexes.ascending("nameNormalized"), Indexes.descending("population")),
                new IndexOptions().name("nameNormalized_1_population_-1"));
        ensure(places, Indexes.ascending("region"), new IndexOptions().name("region_1"));
        ensure(places, Indexes.geo2dsphere("point"), new IndexOptions().name("point_2dsphere"));

        ensure(mongo.getCollection("submissions"),
                Indexes.compoundIndex(Indexes.ascending("status"), Indexes.descending("createdAt")),
                new IndexOptions().name("status_1_createdAt_-1"));
        ensure(mongo.getCollection("jobs_runs"),
                Indexes.compoundIndex(Indexes.ascending("job"), Indexes.descending("startedAt")),
                new IndexOptions().name("job_1_startedAt_-1"));
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
