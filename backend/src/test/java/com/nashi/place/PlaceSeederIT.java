package com.nashi.place;

import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * The seeder is off in the test profile ({@code nashi.places.seed.enabled=false}), so the shared database stays
 * empty for the other ITs; these tests run their own enabled instance and clear {@code places} and
 * {@code places_meta} before and after each test.
 */
class PlaceSeederIT extends MongoIT {

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private PlaceSeeder springSeeder;

    @Autowired
    private PlaceStore store;

    private PlaceSeeder seeder;

    @BeforeEach
    void clean() {
        PlaceFixtures.clear(mongo);
        mongo.dropCollection(PlaceSeeder.META_COLLECTION);
        seeder = new PlaceSeeder(mongo, true);
    }

    @AfterEach
    void cleanUp() {
        PlaceFixtures.clear(mongo);
        mongo.dropCollection(PlaceSeeder.META_COLLECTION);
    }

    private long places() {
        return mongo.count(new Query(), Place.class);
    }

    private void rename(String id, String name) {
        mongo.updateFirst(Query.query(where("_id").is(id)), Update.update("name", name), Place.class);
    }

    private String storedVersion() {
        Document meta = mongo.getCollection(PlaceSeeder.META_COLLECTION).find(new Document("_id", "seed")).first();
        return meta == null ? null : meta.getString("version");
    }

    @Test
    void seedsEmptyCollectionOnce() {
        seeder.run(null);

        assertThat(places()).isGreaterThanOrEqualTo(25_000);
        assertThat(storedVersion()).isEqualTo(seeder.seedVersion());
        assertThat(store.find("Q36036")).get().satisfies(lviv -> {
            assertThat(lviv.name()).isEqualTo("Львів");
            assertThat(lviv.nameNormalized()).isEqualTo("львів");
            assertThat(lviv.type()).isEqualTo(PlaceType.CITY);
            assertThat(lviv.region()).isEqualTo(Region.LVIV);
        });

        rename("Q36036", "змінено");
        assertThat(seeder.seed()).isZero();
        assertThat(store.find("Q36036")).get().extracting(Place::name).isEqualTo("змінено");
    }

    @Test
    void reseedsWhenVersionChanges() {
        seeder.run(null);
        long count = places();
        rename("Q36036", "змінено");
        mongo.getCollection(PlaceSeeder.META_COLLECTION)
                .updateOne(new Document("_id", "seed"), new Document("$set", new Document("version", "old")));

        assertThat(seeder.seed()).isEqualTo(count);

        assertThat(places()).isEqualTo(count);
        assertThat(store.find("Q36036")).get().extracting(Place::name).isEqualTo("Львів");
        assertThat(storedVersion()).isEqualTo(seeder.seedVersion());
    }

    @Test
    void reseedKeepsPlacesAddedByImports() {
        store.ensure("Q999999999", "Хутір поза сидом", Region.SUMY, 50.9, 34.8);
        mongo.getCollection(PlaceSeeder.META_COLLECTION)
                .insertOne(new Document("_id", "seed").append("version", "old"));

        seeder.seed();

        assertThat(store.find("Q999999999")).get().extracting(Place::type).isEqualTo(PlaceType.OTHER);
    }

    @Test
    void disabledSeederWritesNothing() {
        new PlaceSeeder(mongo, false).run(null);

        assertThat(places()).isZero();
        assertThat(storedVersion()).isNull();
    }

    @Test
    void testProfileTurnsTheStartupSeedOff() {
        assertThat(springSeeder.enabled()).isFalse();
    }
}
