package com.nashi.migration;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Names;
import com.nashi.artist.SlugAllocator;
import com.nashi.audio.AudioDuration;
import com.nashi.geo.Region;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRunLog;
import com.nashi.media.FileStorage;
import com.nashi.media.ImageProcessor;
import com.nashi.media.ProcessedImage;
import com.nashi.media.S3FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceSearchService;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceSuggestion;
import com.nashi.telegram.TelegramGateway;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Moves the V1 musicians into V2, started by {@code /migrate_v1 dry|run} and running in this process on
 * {@code jobsExecutor}, one run at a time.
 * <p>
 * In scope are V1 artists with {@code status = APPROVED} and no {@code source} (added through the form), except
 * those whose categories are only {@code VISUAL_ARTIST} and/or {@code WRITER}. V1's Wikipedia artists are not copied;
 * the Wikipedia import creates them. Those of them that V1 administrators hid are copied to {@code wiki_denylist}, so
 * the import does not bring them back. A run is idempotent: an artist already migrated ({@code legacyV1Id}) is skipped.
 * Photos and demo tracks are copied from the V1 bucket before the artist is inserted, and a file that fails is
 * reported without stopping its artist.
 * <p>
 * A dry run writes nothing to {@code artists}, S3 or {@code wiki_denylist}; it records one {@code jobs_runs} entry.
 */
@Service
public class V1MigrationJob {

    private static final Logger log = LoggerFactory.getLogger(V1MigrationJob.class);

    static final String DENYLIST = "wiki_denylist";
    static final double NEAREST_PLACE_METERS = 15_000;
    private static final String DEFAULT_DATABASE = "nashi";

    private final V1MigrationProperties props;
    private final ArtistRepository artists;
    private final PlaceStore places;
    private final PlaceSearchService placeSearch;
    private final FileStorage storage;
    private final ImageProcessor imageProcessor;
    private final MongoTemplate mongo;
    private final ApplicationEventPublisher events;
    private final JobRunLog runLog;
    private final TelegramGateway gateway;
    private final Executor executor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public V1MigrationJob(V1MigrationProperties props, ArtistRepository artists, PlaceStore places,
                          PlaceSearchService placeSearch, FileStorage storage, ImageProcessor imageProcessor,
                          MongoTemplate mongo, ApplicationEventPublisher events, JobRunLog runLog,
                          TelegramGateway gateway, @Qualifier("jobsExecutor") Executor executor, Clock clock) {
        this.props = props;
        this.artists = artists;
        this.places = places;
        this.placeSearch = placeSearch;
        this.storage = storage;
        this.imageProcessor = imageProcessor;
        this.mongo = mongo;
        this.events = events;
        this.runLog = runLog;
        this.gateway = gateway;
        this.executor = executor;
        this.clock = clock;
    }

    /** False when {@code nashi.migration.v1.mongo-uri} is not set. */
    public boolean isConfigured() {
        return props.configured();
    }

    /**
     * Queues a run on {@code jobsExecutor}; its summary goes to the moderation group when it ends.
     *
     * @return false when not configured, when a run is queued or running, or when the queue is full
     */
    public boolean startInBackground(boolean dryRun) {
        if (!isConfigured()) {
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("[V1] a migration is already running");
            return false;
        }
        // jobsExecutor drops a task silently when its queue is full; the flag would then never be cleared
        if (executor instanceof ThreadPoolTaskExecutor pool
                && pool.getThreadPoolExecutor().getQueue().remainingCapacity() == 0) {
            running.set(false);
            log.warn("[V1] the jobs queue is full; the migration was not started");
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runNow(dryRun);
                } catch (RuntimeException e) {
                    // JobRunLog has recorded, logged and announced the failure
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            running.set(false);
            log.warn("[V1] the migration could not be queued: {}", e.getMessage());
            return false;
        }
    }

    /** One logged run in this thread; its summary goes to the group. */
    MigrationReport runNow(boolean dryRun) {
        AtomicReference<MigrationReport> result = new AtomicReference<>();
        runLog.run(JobKind.V1_MIGRATION, () -> {
            result.set(run(dryRun));
            return result.get().toMap(dryRun);
        });
        MigrationReport report = result.get();
        log.info("[V1] {}", report.toTelegramText(dryRun));
        try {
            gateway.sendText(report.toTelegramText(dryRun));
        } catch (RuntimeException e) {
            log.warn("[V1] the migration summary could not be sent: {}", e.getMessage());
        }
        return report;
    }

    /**
     * Migrates (or, with {@code dryRun}, only counts). Opens its own connections to the V1 database and bucket and
     * closes them at the end.
     *
     * @throws RuntimeException when V1 cannot be read; artists inserted before it stay
     */
    public MigrationReport run(boolean dryRun) {
        if (!isConfigured()) {
            throw new IllegalStateException("nashi.migration.v1.mongo-uri is not set");
        }
        Counters counters = new Counters();
        ConnectionString uri = new ConnectionString(props.mongoUri());
        S3FileStorage v1Files = props.s3Bucket() == null || props.s3Bucket().isBlank() ? null
                : S3FileStorage.forBucket(props.s3Endpoint(), props.s3Region(), props.s3Bucket(),
                        props.s3AccessKeyId(), props.s3SecretAccessKey());
        try (MongoClient client = MongoClients.create(uri)) {
            MongoCollection<Document> v1Artists = client
                    .getDatabase(uri.getDatabase() == null ? DEFAULT_DATABASE : uri.getDatabase())
                    .getCollection("artists");
            copyHiddenWikipediaToDenylist(v1Artists, dryRun, counters);
            for (Document v1 : v1Artists.find(Filters.and(Filters.eq("status", "APPROVED"),
                    Filters.eq("source", null)))) {
                counters.found++;
                migrateOne(v1, v1Files, dryRun, counters);
            }
        } finally {
            if (v1Files != null) {
                v1Files.close();
            }
        }
        return counters.report();
    }

    private void copyHiddenWikipediaToDenylist(MongoCollection<Document> v1Artists, boolean dryRun, Counters counters) {
        for (Document v1 : v1Artists.find(Filters.and(Filters.eq("status", "HIDDEN"),
                Filters.eq("source", "WIKIPEDIA")))) {
            String qid = blankToNull(v1.getString("wikidataId"));
            String title = blankToNull(v1.getString("wikiTitle"));
            if (qid == null && title == null) {
                counters.errors.add(V1ArtistMapper.idOf(v1) + ": hidden Wikipedia artist has neither id nor title");
                continue;
            }
            counters.denylisted++;
            if (!dryRun) {
                mongo.upsert(Query.query(Criteria.where("_id").is(qid != null ? qid : "title:" + title)),
                        new Update().set("wikidataId", qid).set("wikiTitle", title).set("reason", "V1_HIDDEN"),
                        DENYLIST);
            }
        }
    }

    private void migrateOne(Document v1, S3FileStorage v1Files, boolean dryRun, Counters counters) {
        String v1Id = V1ArtistMapper.idOf(v1);
        if (V1ArtistMapper.isNonMusic(v1)) {
            counters.skippedNonMusic++;
            return;
        }
        if (artists.findByLegacyV1Id(v1Id).isPresent()) {
            counters.skippedExisting++;
            return;
        }
        String name = v1.getString("name");
        if (name == null || name.isBlank()) {
            counters.errors.add(v1Id + ": artist without a name");
            return;
        }
        if (!artists.findByStatusAndNameNormalized(ArtistStatus.PUBLISHED, Names.normalize(name)).isEmpty()) {
            counters.skippedDuplicateName++;
            return;
        }
        List<String> uploaded = new ArrayList<>();
        try {
            Place place = placeOf(v1).orElse(null);
            String id = new ObjectId().toHexString();
            Artist artist = V1ArtistMapper.map(v1, place, id, SlugAllocator.allocate(name, artists::existsBySlug));
            if (!dryRun) {
                artist.setImages(copyImages(v1, id, name, v1Files, uploaded, counters));
                artist.setAudio(copyDemoTracks(v1, id, v1Files, uploaded, counters));
                artist.setPublishedAt(clock.instant());
                artists.insert(artist);
                events.publishEvent(new ArtistChangedEvent(id));
            }
            counters.migrated++;
            if (place == null) {
                counters.withoutPlace++;
            }
        } catch (RuntimeException e) {
            log.warn("[V1] could not migrate artist {}", v1Id, e);
            counters.errors.add(v1Id + ": artist not migrated (" + e.getClass().getSimpleName() + ")");
            uploaded.forEach(this::deleteQuietly);
        }
    }

    /** Exact name in the region, then a name starting with the city, then the closest place within 15 km. */
    private Optional<Place> placeOf(Document v1) {
        Region region = V1ArtistMapper.regionOf(v1);
        String city = Names.normalize(v1.getString("city"));
        if (region != null && !city.isEmpty()) {
            Optional<Place> exact = places.findExact(city, region);
            if (exact.isPresent()) {
                return exact;
            }
            if (city.length() >= 2) {
                Optional<Place> byPrefix = placeSearch.search(city, region, 1).stream().findFirst()
                        .map(PlaceSuggestion::placeId).flatMap(places::find);
                if (byPrefix.isPresent()) {
                    return byPrefix;
                }
            }
        }
        GeoJsonPoint location = V1ArtistMapper.locationOf(v1);
        return location == null ? Optional.empty()
                : places.nearest(location.getY(), location.getX(), NEAREST_PLACE_METERS);
    }

    private List<ImageAsset> copyImages(Document v1, String artistId, String name, S3FileStorage v1Files,
                                        List<String> uploaded, Counters counters) {
        List<ImageAsset> images = new ArrayList<>();
        for (String key : strings(v1.get("imageKeys"))) {
            if (isUrl(key)) {
                images.add(new ImageAsset(null, key, null, null, name, null));
                continue;
            }
            try {
                ProcessedImage processed = imageProcessor.process(read(v1Files, key), "images[" + images.size() + "]");
                String full = "artists/" + artistId + "/" + images.size() + ".jpg";
                String thumb = "artists/" + artistId + "/" + images.size() + "-thumb.jpg";
                storage.put(full, processed.full(), "image/jpeg");
                uploaded.add(full);
                storage.put(thumb, processed.thumb(), "image/jpeg");
                uploaded.add(thumb);
                images.add(new ImageAsset(full, null, thumb, null, name, null));
            } catch (RuntimeException e) {
                fileFailed(V1ArtistMapper.idOf(v1), key, e, counters);
            }
        }
        return List.copyOf(images);
    }

    private List<AudioClip> copyDemoTracks(Document v1, String artistId, S3FileStorage v1Files,
                                           List<String> uploaded, Counters counters) {
        List<AudioClip> clips = new ArrayList<>();
        for (String key : strings(v1.get("demoTrackKeys"))) {
            try {
                String ext = extensionOf(key);
                byte[] bytes = read(v1Files, key);
                int seconds = AudioDuration.seconds(bytes, ext);
                int n = clips.size();
                String target = "artists/" + artistId + "/audio/" + n + "." + ext;
                storage.put(target, bytes, contentType(ext));
                uploaded.add(target);
                clips.add(new AudioClip("up_" + n, AudioSource.UPLOAD, null, target, null, "Демо " + (n + 1), seconds,
                        "Завантажено виконавцем", null));
            } catch (RuntimeException e) {
                fileFailed(V1ArtistMapper.idOf(v1), key, e, counters);
            }
        }
        return List.copyOf(clips);
    }

    private static byte[] read(S3FileStorage v1Files, String key) {
        if (v1Files == null) {
            throw new IllegalStateException("nashi.migration.v1.s3-bucket is not set");
        }
        return v1Files.get(key);
    }

    private void fileFailed(String v1Id, String key, RuntimeException e, Counters counters) {
        log.warn("[V1] file {} of artist {} was not copied", key, v1Id, e);
        counters.errors.add(v1Id + ": " + key + " not copied (" + e.getClass().getSimpleName() + ")");
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("[V1] could not remove {} after a failed insert", key, e);
        }
    }

    private static String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        String ext = dot < 0 ? "" : key.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,5}") ? ext : "mp3";
    }

    private static String contentType(String ext) {
        return switch (ext) {
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            default -> "application/octet-stream";
        };
    }

    private static boolean isUrl(String key) {
        return key.startsWith("http://") || key.startsWith("https://");
    }

    private static List<String> strings(Object value) {
        return value instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).filter(s -> !s.isBlank()).toList()
                : List.of();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static final class Counters {
        int found;
        int migrated;
        int skippedExisting;
        int skippedDuplicateName;
        int skippedNonMusic;
        int withoutPlace;
        int denylisted;
        final List<String> errors = new ArrayList<>();

        MigrationReport report() {
            return new MigrationReport(found, migrated, skippedExisting, skippedDuplicateName, skippedNonMusic,
                    withoutPlace, denylisted, errors);
        }
    }
}
