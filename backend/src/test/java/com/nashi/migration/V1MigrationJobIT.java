package com.nashi.migration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Names;
import com.nashi.geo.Region;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRun;
import com.nashi.jobs.JobRunLog;
import com.nashi.media.FileStorage;
import com.nashi.media.ImageProcessor;
import com.nashi.place.Place;
import com.nashi.place.PlaceSearchService;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceType;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import com.nashi.telegram.TelegramGateway;
import com.nashi.wiki.MongoMusicianStore;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The migration against real Mongo (databases {@code nashi} for V1 and {@code nashi_v2}) and MinIO (two buckets). */
class V1MigrationJobIT extends MongoIT {

    private static final String V1_DATABASE = "nashi";
    private static final String V1_BUCKET = "nashi-v1-test";

    private static MongoClient v1Mongo;
    private static S3Client s3;

    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private PlaceStore places;
    @Autowired
    private PlaceSearchService placeSearch;
    @Autowired
    private FileStorage storage;
    @Autowired
    private ImageProcessor images;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private Clock clock;
    @Autowired
    private RecordedEvents recorded;
    @Autowired
    private MongoMusicianStore store;

    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final List<String> placeIds = new ArrayList<>();
    private V1MigrationJob job;

    @BeforeAll
    static void connectToV1() {
        v1Mongo = MongoClients.create(MONGO.getReplicaSetUrl(V1_DATABASE));
        s3 = S3Client.builder().endpointOverride(URI.create(MINIO.getS3URL()))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .forcePathStyle(true).build();
        s3.createBucket(CreateBucketRequest.builder().bucket(V1_BUCKET).build());
    }

    @AfterAll
    static void disconnect() {
        v1Mongo.close();
        s3.close();
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        place("QT1899", "Київ", Region.KYIV_CITY, 50.45, 30.52);
        place("QT36036", "Львів", Region.LVIV, 49.84, 24.03);
        place("QT465104", "Броди", Region.LVIV, 50.078, 25.154);
        place("QT1018", "Севастополь", Region.SEVASTOPOL_CITY, 44.6, 33.52);
        place("QT1020", "Ялта", Region.CRIMEA, 44.5, 34.17);
        recorded.clear();
        JobRunLog runLog = new JobRunLog(mongo, clock, gateway);
        job = new V1MigrationJob(new V1MigrationProperties(MONGO.getReplicaSetUrl(V1_DATABASE), V1_BUCKET, "us-east-1",
                MINIO.getS3URL(), MINIO.getUserName(), MINIO.getPassword()),
                artists, places, placeSearch, storage, images, mongo, events, runLog, gateway, Runnable::run, clock);
    }

    @AfterEach
    void cleanUp() {
        v1Artists().deleteMany(new Document());
        artists.deleteAll();
        mongo.remove(new Query(), "wiki_denylist");
        mongo.remove(new Query(), JobRun.class);
        placeIds.forEach(id -> mongo.remove(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id")
                .is(id)), Place.class));
        placeIds.clear();
        for (S3Object o : s3.listObjectsV2(ListObjectsV2Request.builder().bucket(V1_BUCKET).build()).contents()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(V1_BUCKET).key(o.key()).build());
        }
    }

    // ---- scope ----

    @Test
    void migratesApprovedFormArtistsWithImagesAndDemoTracks() throws IOException {
        putV1("artists/v1/one.jpg", resource("/images/tiny.jpg"));
        putV1("artists/v1/two.mp3", resource("/audio/sample.mp3"));
        String first = v1Artist("Перший", "APPROVED", null).append("city", "Львів").append("region", "LVIV")
                .append("imageKeys", List.of("artists/v1/one.jpg", "https://img.example.com/p.jpg"))
                .append("demoTrackKeys", List.of("artists/v1/two.mp3")).getObjectId("_id").toHexString();
        v1Artist("Другий", "APPROVED", null).append("city", "Київ").append("region", "KYIV");
        v1Artist("Третій", "PENDING", null);
        v1Artist("Вікі", "APPROVED", "WIKIPEDIA");
        insertAll();

        MigrationReport report = job.run(false);

        assertThat(report.found()).isEqualTo(2);
        assertThat(report.migrated()).isEqualTo(2);
        assertThat(report.errors()).isEmpty();
        Artist migrated = artists.findByLegacyV1Id(first).orElseThrow();
        assertThat(migrated.getSource()).isEqualTo(ArtistSource.V1_MIGRATION);
        assertThat(migrated.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(migrated.getPublishedAt()).isNotNull();
        assertThat(migrated.getSlug()).isEqualTo("pershyi");
        assertThat(migrated.getOrigin().placeId()).isEqualTo("QT36036");
        assertThat(migrated.getPin()).isNotNull();
        String base = "artists/" + migrated.getId();
        assertThat(migrated.getImages()).containsExactly(
                new ImageAsset(base + "/0.jpg", null, base + "/0-thumb.jpg", null, "Перший", null),
                new ImageAsset(null, "https://img.example.com/p.jpg", null, null, "Перший", null));
        assertThat(storage.get(base + "/0.jpg")).isNotEmpty();
        assertThat(storage.get(base + "/0-thumb.jpg")).isNotEmpty();
        assertThat(artists.findByLegacyV1Id(v1Artists().find(new Document("name", "Другий")).first()
                .getObjectId("_id").toHexString())).isPresent();
        assertThat(artists.count()).isEqualTo(2);
        assertThat(recorded.artistEvents()).hasSize(2).allMatch(e -> e instanceof ArtistChangedEvent);
    }

    @Test
    void secondRunSkipsExisting() {
        v1Artist("Перший", "APPROVED", null);
        v1Artist("Другий", "APPROVED", null);
        insertAll();
        job.run(false);

        MigrationReport second = job.run(false);

        assertThat(second.migrated()).isZero();
        assertThat(second.skippedExisting()).isEqualTo(2);
        assertThat(second.skippedDuplicateName()).isZero();
        assertThat(artists.count()).isEqualTo(2);
    }

    @Test
    void skipsNonMusicFormArtists() {
        v1Artist("Художник", "APPROVED", null).append("categories", List.of("VISUAL_ARTIST"));
        v1Artist("Письменник", "APPROVED", null).append("categories", List.of("WRITER", "VISUAL_ARTIST"));
        v1Artist("Поет і співак", "APPROVED", null).append("categories", List.of("WRITER", "MUSICIAN"));
        v1Artist("Гурт", "APPROVED", null).append("categories", List.of("BAND"));
        v1Artist("Без категорій", "APPROVED", null).append("categories", List.of());
        insertAll();

        MigrationReport report = job.run(false);

        assertThat(report.found()).isEqualTo(5);
        assertThat(report.skippedNonMusic()).isEqualTo(2);
        assertThat(report.migrated()).isEqualTo(3);
        assertThat(artists.findAll()).extracting(Artist::getName)
                .containsExactlyInAnyOrder("Поет і співак", "Гурт", "Без категорій");
    }

    @Test
    void skipsDuplicateNameOfPublishedArtist() {
        artists.save(Artist.builder().id(new ObjectId().toHexString()).slug("okean").name("Океан Ельзи")
                .status(ArtistStatus.PUBLISHED).source(ArtistSource.WIKIPEDIA).build());
        artists.save(Artist.builder().id(new ObjectId().toHexString()).slug("hidden").name("Прихований")
                .status(ArtistStatus.HIDDEN).source(ArtistSource.WIKIPEDIA).build());
        v1Artist("«Океан   Ельзи»", "APPROVED", null);
        v1Artist("Прихований", "APPROVED", null);
        insertAll();

        MigrationReport report = job.run(false);

        assertThat(report.skippedDuplicateName()).isEqualTo(1);
        assertThat(report.migrated()).isEqualTo(1);
        assertThat(artists.findByStatusAndNameNormalized(ArtistStatus.PUBLISHED, Names.normalize("Океан Ельзи")))
                .hasSize(1);
    }

    @Test
    void copiesHiddenWikipediaIdsToDenylist() {
        v1Artist("Прихований з ід", "HIDDEN", "WIKIPEDIA").append("wikidataId", "Q4242").append("wikiTitle", "Прихований (гурт)");
        v1Artist("Прихований без ід", "HIDDEN", "WIKIPEDIA").append("wikiTitle", "Тільки Назва");
        v1Artist("Прихований з форми", "HIDDEN", null).append("wikidataId", "Q1");
        v1Artist("Видимий", "APPROVED", "WIKIPEDIA").append("wikidataId", "Q2").append("wikiTitle", "Видимий");
        insertAll();

        MigrationReport report = job.run(false);
        MigrationReport again = job.run(false);

        assertThat(report.denylisted()).isEqualTo(2);
        assertThat(again.denylisted()).isEqualTo(2);
        List<Document> denylist = mongo.findAll(Document.class, "wiki_denylist");
        assertThat(denylist).extracting(d -> d.get("_id")).containsExactlyInAnyOrder("Q4242", "title:Тільки Назва");
        assertThat(denylist).filteredOn(d -> "Q4242".equals(d.get("_id"))).singleElement().satisfies(d -> {
            assertThat(d.getString("wikidataId")).isEqualTo("Q4242");
            assertThat(d.getString("wikiTitle")).isEqualTo("Прихований (гурт)");
            assertThat(d.getString("reason")).isEqualTo("V1_HIDDEN");
        });
        assertThat(store.loadKnown().wikidataIds()).containsExactly("Q4242");
        assertThat(store.loadKnown().wikiTitles()).containsExactlyInAnyOrder("Прихований (гурт)", "Тільки Назва");
        assertThat(artists.count()).isZero();
    }

    // ---- dry run ----

    @Test
    void dryRunWritesNothing() throws IOException {
        putV1("artists/v1/one.jpg", resource("/images/tiny.jpg"));
        v1Artist("Перший", "APPROVED", null).append("imageKeys", List.of("artists/v1/one.jpg"));
        v1Artist("Прихований", "HIDDEN", "WIKIPEDIA").append("wikidataId", "Q4242");
        insertAll();
        int objectsBefore = countObjects(BUCKET, "artists/");

        MigrationReport report = job.run(true);

        assertThat(report.migrated()).isEqualTo(1);
        assertThat(report.denylisted()).isEqualTo(1);
        assertThat(artists.count()).isZero();
        assertThat(mongo.findAll(Document.class, "wiki_denylist")).isEmpty();
        assertThat(countObjects(BUCKET, "artists/")).isEqualTo(objectsBefore);
        assertThat(recorded.artistEvents()).isEmpty();
    }

    // ---- places ----

    @Test
    void fallsBackToNearestPlaceWithin15km() {
        // About 2 km from Brody; the city is not in the gazetteer
        v1Artist("Біля Бродів", "APPROVED", null).append("city", "Хутір Невідомий").append("region", "LVIV")
                .append("location", point(50.095, 25.175));
        // 40 km from anything
        v1Artist("Далеко", "APPROVED", null).append("city", "Хутір Невідомий").append("region", "LVIV")
                .append("location", point(50.3, 25.9));
        insertAll();

        MigrationReport report = job.run(false);

        assertThat(report.migrated()).isEqualTo(2);
        assertThat(report.withoutPlace()).isEqualTo(1);
        assertThat(byName("Біля Бродів").getOrigin().placeId()).isEqualTo("QT465104");
        assertThat(byName("Далеко").getOrigin()).isNull();
        assertThat(byName("Далеко").getPin()).isNull();
    }

    @Test
    void exactCityNameWinsThenPrefixSearch() {
        // «Ялт» is a prefix of «Ялта» only; «Львів» is exact even though the artist's point is near Brody
        v1Artist("Префікс", "APPROVED", null).append("city", "Ялт").append("region", "CRIMEA");
        v1Artist("Точно", "APPROVED", null).append("city", "Львів").append("region", "LVIV")
                .append("location", point(50.095, 25.175));
        insertAll();

        job.run(false);

        assertThat(byName("Префікс").getOrigin().placeId()).isEqualTo("QT1020");
        assertThat(byName("Точно").getOrigin().placeId()).isEqualTo("QT36036");
    }

    @Test
    void sevastopolResolvesToSevastopolCity() {
        v1Artist("Севастопольський", "APPROVED", null).append("city", "Севастополь").append("region", "CRIMEA");
        v1Artist("Київський", "APPROVED", null).append("city", "Київ").append("region", "KYIV");
        insertAll();

        job.run(false);

        assertThat(byName("Севастопольський").getOrigin().region()).isEqualTo(Region.SEVASTOPOL_CITY);
        assertThat(byName("Севастопольський").getOrigin().placeId()).isEqualTo("QT1018");
        assertThat(byName("Київський").getOrigin().region()).isEqualTo(Region.KYIV_CITY);
    }

    // ---- files ----

    @Test
    void demoTrackBecomesUploadClipWithDuration() throws IOException {
        putV1("artists/v1/demo.mp3", resource("/audio/sample.mp3"));
        v1Artist("З демо", "APPROVED", null).append("demoTrackKeys", List.of("artists/v1/demo.mp3"));
        insertAll();

        MigrationReport report = job.run(false);

        assertThat(report.errors()).isEmpty();
        Artist artist = byName("З демо");
        String key = "artists/" + artist.getId() + "/audio/0.mp3";
        assertThat(artist.getAudio()).containsExactly(new AudioClip("up_0", AudioSource.UPLOAD, null, key, null,
                "Демо 1", 3, "Завантажено виконавцем", null));
        assertThat(storage.get(key)).isEqualTo(resource("/audio/sample.mp3"));
    }

    @Test
    void aFileThatFailsIsReportedAndTheArtistIsStillMigrated() throws IOException {
        putV1("artists/v1/ok.mp3", resource("/audio/sample.mp3"));
        putV1("artists/v1/broken.jpg", new byte[]{1, 2, 3});
        v1Artist("Зі збоєм", "APPROVED", null)
                .append("imageKeys", List.of("artists/v1/missing.jpg", "artists/v1/broken.jpg"))
                .append("demoTrackKeys", List.of("artists/v1/ok.mp3", "artists/v1/missing.mp3"));
        insertAll();

        MigrationReport report = job.run(false);

        assertThat(report.migrated()).isEqualTo(1);
        assertThat(report.errors()).hasSize(3);
        Artist artist = byName("Зі збоєм");
        assertThat(artist.getImages()).isEmpty();
        assertThat(artist.getAudio()).extracting(AudioClip::clipId).containsExactly("up_0");
    }

    // ---- background run ----

    @Test
    void backgroundRunIsLoggedAndSummarySentToTheGroup() {
        v1Artist("Перший", "APPROVED", null);
        insertAll();

        assertThat(job.startInBackground(false)).isTrue();

        assertThat(artists.count()).isEqualTo(1);
        JobRun run = new JobRunLog(mongo, clock, gateway).lastRun(JobKind.V1_MIGRATION).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(JobRunLog.OK);
        verify(gateway).sendText(org.mockito.ArgumentMatchers.contains("перенесено 1"));
        // the flag is reset: another run can start
        assertThat(job.startInBackground(true)).isTrue();
    }

    @Test
    void notConfiguredJobDoesNotStart() {
        V1MigrationJob unconfigured = new V1MigrationJob(new V1MigrationProperties(null, null, null, null, null, null),
                artists, places, placeSearch, storage, images, mongo, events,
                new JobRunLog(mongo, clock, gateway), gateway, Runnable::run, clock);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.startInBackground(false)).isFalse();
        verifyNoInteractions(gateway);
    }

    // ---- helpers ----

    private final List<Document> pending = new ArrayList<>();

    private Document v1Artist(String name, String status, String source) {
        Document doc = new Document("_id", new ObjectId()).append("name", name).append("status", status)
                .append("source", source).append("slug", "v1-" + name.hashCode());
        pending.add(doc);
        return doc;
    }

    private void insertAll() {
        v1Artists().insertMany(pending);
        pending.clear();
    }

    private MongoCollection<Document> v1Artists() {
        return v1Mongo.getDatabase(V1_DATABASE).getCollection("artists");
    }

    private void place(String id, String name, Region region, double lat, double lon) {
        mongo.save(new Place(id, name, Names.normalize(name), PlaceType.CITY, region, new GeoJsonPoint(lon, lat), 10.0,
                1000L));
        placeIds.add(id);
    }

    private static Document point(double lat, double lon) {
        return new Document("type", "Point").append("coordinates", List.of(lon, lat));
    }

    private Artist byName(String name) {
        return artists.findAll().stream().filter(a -> name.equals(a.getName())).findFirst().orElseThrow();
    }

    private static void putV1(String key, byte[] bytes) {
        s3.putObject(PutObjectRequest.builder().bucket(V1_BUCKET).key(key).build(), RequestBody.fromBytes(bytes));
    }

    private static int countObjects(String bucket, String prefix) {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .contents().size();
    }

    private byte[] resource(String path) throws IOException {
        try (var in = getClass().getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }
}
