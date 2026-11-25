package com.nashi.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Creates the MongoDB indexes on startup (Spring Data's auto-index creation is off).
 * Each index is tried on its own: one that fails (e.g. it already exists under another name) is logged
 * and the others are still created.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @Bean
    public CommandLineRunner ensureIndexes() {
        return args -> {
            log.info("Ensuring MongoDB indexes exist...");
            MongoCollection<Document> artists = mongoTemplate.getCollection("artists");

            // Geospatial queries for the map
            ensure(artists, Indexes.geo2dsphere("location"),
                    new IndexOptions().name("location_2dsphere"));
            // The Wikipedia import relies on this index to never store the same Wikidata item twice.
            ensure(artists, Indexes.ascending("wikidataId"),
                    new IndexOptions().name("wikidataId_unique").unique(true).sparse(true));
            // Artist pages are looked up by slug.
            ensure(artists, Indexes.ascending("slug"),
                    new IndexOptions().name("slug_unique").unique(true));
            // Public lists: APPROVED artists, optionally in one region.
            ensure(artists, Indexes.ascending("status", "region"),
                    new IndexOptions().name("status_1_region_1"));
            ensure(artists, Indexes.ascending("categories"),
                    new IndexOptions().name("categories_1"));
        };
    }

    private static void ensure(MongoCollection<Document> collection, Bson keys, IndexOptions options) {
        try {
            collection.createIndex(keys, options);
            log.info("Ensured index {}", options.getName());
        } catch (Exception e) {
            log.warn("Could not create index {} (may already exist): {}", options.getName(), e.getMessage());
        }
    }
}
