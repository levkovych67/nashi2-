package com.nashi.place;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import com.nashi.place.seed.PlaceSeedRow;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Loads the committed Wikidata seed ({@code geo/places-ua.json.gz}, one {@link PlaceSeedRow} per line) into
 * {@code places} on startup, right after the indexes. The seed's version ({@code geo/places-ua.version}) is kept in
 * {@code places_meta {_id: "seed", version}}: when it matches, nothing is written; when it differs (or the
 * collection was never seeded), every seed row is upserted by QID in unordered batches of {@value #BATCH}. Places
 * that are not in the seed (added by imports through {@link PlaceStore#ensure}) are kept.
 * <p>
 * {@code nashi.places.seed.enabled=false} (the test profile) turns the startup run off.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PlaceSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceSeeder.class);

    public static final String META_COLLECTION = "places_meta";
    static final String SEED_RESOURCE = "geo/places-ua.json.gz";
    static final String VERSION_RESOURCE = "geo/places-ua.version";
    static final int BATCH = 1000;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MongoTemplate mongo;
    private final boolean enabled;
    private final String seedResource;
    private final String versionResource;

    @Autowired
    public PlaceSeeder(MongoTemplate mongo, @Value("${nashi.places.seed.enabled:true}") boolean enabled) {
        this(mongo, enabled, SEED_RESOURCE, VERSION_RESOURCE);
    }

    PlaceSeeder(MongoTemplate mongo, boolean enabled, String seedResource, String versionResource) {
        this.mongo = mongo;
        this.enabled = enabled;
        this.seedResource = seedResource;
        this.versionResource = versionResource;
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Places seed is off (nashi.places.seed.enabled=false)");
            return;
        }
        seed();
    }

    /** @return how many seed rows were upserted; 0 when the stored version is the seed's */
    public long seed() {
        String version = seedVersion();
        MongoCollection<Document> meta = mongo.getCollection(META_COLLECTION);
        Document stored = meta.find(Filters.eq("_id", "seed")).first();
        if (stored != null && version.equals(stored.getString("version"))) {
            log.info("Places seed {} is already loaded", version);
            return 0;
        }

        long started = System.nanoTime();
        MongoCollection<Document> places = mongo.getCollection(mongo.getCollectionName(Place.class));
        List<WriteModel<Document>> batch = new ArrayList<>(BATCH);
        long written = 0;
        try (BufferedReader reader = open(seedResource)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                PlaceSeedRow row = JSON.readValue(line, PlaceSeedRow.class);
                Document doc = new Document();
                mongo.getConverter().write(row.toPlace(), doc);
                batch.add(new ReplaceOneModel<>(Filters.eq("_id", row.qid()), doc, new ReplaceOptions().upsert(true)));
                if (batch.size() == BATCH) {
                    written += flush(places, batch);
                }
            }
            written += flush(places, batch);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + seedResource, e);
        }
        meta.replaceOne(Filters.eq("_id", "seed"), new Document("_id", "seed").append("version", version),
                new ReplaceOptions().upsert(true));
        log.info("Seeded {} places (version {}, previously {}) in {} ms", written, version,
                stored == null ? "none" : stored.getString("version"), (System.nanoTime() - started) / 1_000_000);
        return written;
    }

    /** The version of the seed on the classpath, e.g. {@code 2026-11-18+3f9c0a1b}. */
    public String seedVersion() {
        try (InputStream in = new ClassPathResource(versionResource).getInputStream()) {
            String version = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (version.isEmpty()) {
                throw new IllegalStateException(versionResource + " is empty");
            }
            return version;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + versionResource, e);
        }
    }

    private static BufferedReader open(String resource) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ClassPathResource(resource).getInputStream()), StandardCharsets.UTF_8));
    }

    private static int flush(MongoCollection<Document> places, List<WriteModel<Document>> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        places.bulkWrite(batch, new BulkWriteOptions().ordered(false));
        int n = batch.size();
        batch.clear();
        return n;
    }
}
