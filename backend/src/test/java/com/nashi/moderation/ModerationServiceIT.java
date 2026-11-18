package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.geo.PinPlacer;
import com.nashi.geo.PlaceRef;
import com.nashi.geo.Region;
import com.nashi.media.FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Moderator;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import com.nashi.telegram.Verdict;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Review Focus 1: the status flip is atomic, so concurrent or late clicks create exactly one artist, and a failure
 * after the flip puts the submission back to {@code PENDING}. The spies only change behaviour where a test says so.
 */
class ModerationServiceIT extends MongoIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Moderator ADMIN1 = new Moderator(101L, "admin1", "Адмін Один");
    private static final Moderator ADMIN2 = new Moderator(102L, "admin2", "Адмін Два");
    private static final String WIKI = "https://uk.wikipedia.org/wiki/Океан_Ельзи";
    private static final Place LVIV = new Place("Q36036", "Львів", "львів", PlaceType.CITY, Region.LVIV,
            new GeoJsonPoint(24.0315921, 49.841952), 182.0, 717_273L);

    @MockitoSpyBean
    private FileStorage storage;
    @MockitoSpyBean
    private ArtistFromSubmission artistFactory;

    @Autowired
    private ModerationService moderation;
    @Autowired
    private SubmissionRepository submissions;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private RestTestClient http;
    @Autowired
    private RecordedEvents events;
    @Autowired
    private ApplicationEventPublisher publisher;

    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        submissions.deleteAll();
        artists.deleteAll();
        mongo.save(LVIV);
        events.clear();
    }

    @AfterEach
    void tearDown() {
        for (Artist a : artists.findAll()) {
            if (a.getImages() != null) {
                a.getImages().forEach(i -> keys.addAll(List.of(i.key(), i.thumbKey())));
            }
        }
        keys.forEach(storage::delete);
        submissions.deleteAll();
        artists.deleteAll();
        mongo.remove(Query.query(Criteria.where("_id").is(LVIV.id())), Place.class);
        publisher.publishEvent(new ArtistChangedEvent("any"));
    }

    /** A PENDING submission with two photos (full and thumbnail each) in the private prefix. */
    private Submission pending(String name) {
        String id = new ObjectId().toHexString();
        List<String> imageKeys = new ArrayList<>();
        for (int n = 0; n < 2; n++) {
            String prefix = "submissions/" + id + "/" + n;
            for (String key : List.of(prefix + ".jpg", prefix + "-thumb.jpg")) {
                storage.put(key, bytes(key), "image/jpeg");
                imageKeys.add(key);
            }
        }
        keys.addAll(imageKeys);
        return submissions.save(Submission.builder().id(id).status(SubmissionStatus.PENDING)
                .name(name).kind(MusicianKind.BAND).originPlaceId(LVIV.id())
                .summary("Гурт грає рок уже тридцять років і збирає стадіони.")
                .genres(List.of("рок")).links(List.of("https://open.spotify.com/artist/abc"))
                .wikipediaUrl(WIKI).consentAt(Instant.now()).imageKeys(imageKeys).moderationLog(List.of())
                .build());
    }

    private static byte[] bytes(String key) {
        return ("jpeg of " + key).getBytes();
    }

    private Submission reload(Submission s) {
        return submissions.findById(s.getId()).orElseThrow();
    }

    private List<Artist> artistsOf(Submission s) {
        return artists.findAll().stream().filter(a -> s.getId().equals(a.getSubmissionId())).toList();
    }

    @Test
    void approveCreatesPublishedArtistOnMap() {
        Submission s = pending("Океан Ельзи");

        DecisionResult result = moderation.decide(s.getId(), Verdict.YES, ADMIN1);

        Artist artist = artists.findById(result.artist().getId()).orElseThrow();
        String aid = artist.getId();
        assertThat(artist.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(artist.getSource()).isEqualTo(ArtistSource.SUBMISSION);
        assertThat(artist.getSubmissionId()).isEqualTo(s.getId());
        assertThat(artist.getSlug()).isEqualTo("okean-elzy");
        assertThat(artist.getNameNormalized()).isEqualTo("океан ельзи");
        assertThat(artist.getCreatedAt()).as("auditing with a pre-assigned id").isNotNull();
        assertThat(artist.getPublishedAt()).isNotNull();
        assertThat(artist.getVersion()).isZero();
        assertThat(artist.getOrigin()).isEqualTo(new Origin(LVIV.id(), "Львів", Region.LVIV, OriginBasis.FORMATION,
                PinPrecision.CITY, LVIV.point()));
        assertThat(artist.getPin()).isEqualTo(PinPlacer.pinFor(aid, PlaceRef.of(LVIV), PinPrecision.CITY));
        String from = "submissions/" + s.getId() + "/";
        String to = "artists/" + aid + "/";
        assertThat(artist.getImages()).containsExactly(
                new ImageAsset(to + "0.jpg", null, to + "0-thumb.jpg", null, "Океан Ельзи", null),
                new ImageAsset(to + "1.jpg", null, to + "1-thumb.jpg", null, "Океан Ельзи", null));
        for (String n : List.of("0", "0-thumb", "1", "1-thumb")) {
            assertThat(storage.get(to + n + ".jpg")).isEqualTo(bytes(from + n + ".jpg"));
        }

        Submission stored = reload(s);
        assertThat(stored.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(stored.getDecidedAt()).isNotNull();
        assertThat(stored.getArtistId()).isEqualTo(aid);
        assertThat(result.submission().getArtistId()).isEqualTo(aid);

        assertThat(events.artistEvents()).containsExactly(new ArtistChangedEvent(aid),
                new EnrichmentRequested(aid, WIKI), new AudioResolveRequested(aid));

        EntityExchangeResult<byte[]> pins = http.get().uri("/api/v2/map/pins").exchange().returnResult(byte[].class);
        assertThat(pins.getStatus()).isEqualTo(HttpStatus.OK);
        List<String> slugs = new ArrayList<>();
        JSON.readTree(pins.getResponseBody()).get("pins").forEach(p -> slugs.add(p.get("slug").asString()));
        assertThat(slugs).contains("okean-elzy");
    }

    @Test
    void rejectPublishesNothing() {
        Submission s = pending("Океан Ельзи");

        DecisionResult result = moderation.decide(s.getId(), Verdict.NO, ADMIN1);

        assertThat(result.artist()).isNull();
        Submission stored = reload(s);
        assertThat(stored.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(stored.getDecidedAt()).isNotNull();
        assertThat(stored.getArtistId()).isNull();
        assertThat(artists.count()).isZero();
        verify(storage, never()).copy(anyString(), anyString());
        assertThat(events.artistEvents()).isEmpty();
    }

    @Test
    void concurrentApprovalsCreateOneArtist() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                Submission s = pending("Гурт " + round);
                CyclicBarrier start = new CyclicBarrier(2);
                List<Future<DecisionResult>> futures = new ArrayList<>();
                for (Moderator by : List.of(ADMIN1, ADMIN2)) {
                    Callable<DecisionResult> click = () -> {
                        start.await(5, TimeUnit.SECONDS);
                        return moderation.decide(s.getId(), Verdict.YES, by);
                    };
                    futures.add(pool.submit(click));
                }
                List<DecisionResult> won = new ArrayList<>();
                List<AlreadyDecidedException> lost = new ArrayList<>();
                for (Future<DecisionResult> f : futures) {
                    try {
                        won.add(f.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException e) {
                        assertThat(e.getCause()).isInstanceOf(AlreadyDecidedException.class);
                        lost.add((AlreadyDecidedException) e.getCause());
                    }
                }

                assertThat(won).as("round %d", round).hasSize(1);
                assertThat(lost).as("round %d", round).hasSize(1);
                assertThat(lost.getFirst().currentStatus()).isEqualTo(SubmissionStatus.APPROVED);
                List<Artist> created = artistsOf(s);
                assertThat(created).as("round %d", round).hasSize(1);
                Submission stored = reload(s);
                assertThat(stored.getArtistId()).isEqualTo(created.getFirst().getId());
                assertThat(stored.getModerationLog()).extracting(ModerationEntry::action)
                        .containsExactly(ModerationAction.APPROVED);
                assertThat(events.artistEvents()).filteredOn(ArtistChangedEvent.class::isInstance)
                        .containsOnlyOnce(new ArtistChangedEvent(created.getFirst().getId()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void decisionIsLoggedWithModerator() {
        Submission s = pending("Океан Ельзи");

        moderation.decide(s.getId(), Verdict.YES, ADMIN1);

        Submission stored = reload(s);
        ModerationEntry last = stored.getModerationLog().getLast();
        assertThat(last.action()).isEqualTo(ModerationAction.APPROVED);
        assertThat(last.by().username()).isEqualTo("admin1");
        assertThat(last.by()).isEqualTo(ADMIN1);
        assertThat(last.at()).isEqualTo(stored.getDecidedAt());
    }

    @Test
    void oldButtonAfterHideChangesNothing() {
        Submission s = pending("Океан Ельзи");
        String aid = moderation.decide(s.getId(), Verdict.YES, ADMIN1).artist().getId();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(aid)), Update.update("status", ArtistStatus.HIDDEN),
                Artist.class);
        Submission before = reload(s);
        events.clear();

        for (Verdict verdict : Verdict.values()) {
            assertThatThrownBy(() -> moderation.decide(s.getId(), verdict, ADMIN2))
                    .isInstanceOfSatisfying(AlreadyDecidedException.class,
                            e -> assertThat(e.currentStatus()).isEqualTo(SubmissionStatus.APPROVED));
        }

        Submission after = reload(s);
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getModerationLog()).isEqualTo(before.getModerationLog());
        assertThat(after.getDecidedAt()).isEqualTo(before.getDecidedAt());
        assertThat(artists.count()).isEqualTo(1);
        assertThat(artists.findById(aid).orElseThrow().getStatus()).isEqualTo(ArtistStatus.HIDDEN);
        assertThat(events.artistEvents()).isEmpty();
    }

    @Test
    void artistCreationFailureRevertsToPending() {
        Submission s = pending("Океан Ельзи");
        AtomicInteger copies = new AtomicInteger();
        doAnswer(inv -> {
            if (copies.incrementAndGet() == 3) {
                throw SdkClientException.create("S3 unavailable");
            }
            return inv.callRealMethod();
        }).when(storage).copy(anyString(), anyString());

        assertThatThrownBy(() -> moderation.decide(s.getId(), Verdict.YES, ADMIN1))
                .isInstanceOf(SdkClientException.class);

        Submission reverted = reload(s);
        assertThat(reverted.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(reverted.getDecidedAt()).isNull();
        assertThat(reverted.getArtistId()).isNull();
        ModerationEntry last = reverted.getModerationLog().getLast();
        assertThat(last.action()).isEqualTo(ModerationAction.FAILED);
        assertThat(last.by()).isEqualTo(ADMIN1);
        assertThat(last.note()).isEqualTo("SdkClientException: S3 unavailable");
        assertThat(artists.count()).isZero();
        assertThat(events.artistEvents()).isEmpty();
        ArgumentCaptor<String> copiedTo = ArgumentCaptor.forClass(String.class);
        verify(storage, atLeastOnce()).copy(anyString(), copiedTo.capture());
        List<String> attempted = copiedTo.getAllValues();
        assertThat(attempted).hasSize(3);
        for (String key : attempted.subList(0, 2)) {
            assertThatThrownBy(() -> storage.get(key)).as(key).isInstanceOf(NoSuchKeyException.class);
        }

        doCallRealMethod().when(storage).copy(anyString(), anyString());
        DecisionResult retried = moderation.decide(s.getId(), Verdict.YES, ADMIN1);

        Submission approved = reload(s);
        assertThat(approved.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(approved.getArtistId()).isEqualTo(retried.artist().getId());
        assertThat(approved.getModerationLog()).extracting(ModerationEntry::action)
                .containsExactly(ModerationAction.APPROVED, ModerationAction.FAILED, ModerationAction.APPROVED);
        assertThat(artistsOf(s)).hasSize(1);
    }

    @Test
    void slugTakenBetweenAllocationAndSaveIsAllocatedAgain() {
        Submission s = pending("Океан Ельзи");
        doAnswer(inv -> {
            Artist created = (Artist) inv.callRealMethod();
            // another approval saves the same slug first
            artists.save(Artist.builder().id(new ObjectId().toHexString()).slug(created.getSlug()).name("Інший")
                    .status(ArtistStatus.PUBLISHED).build());
            return created;
        }).when(artistFactory).create(any(Submission.class));

        DecisionResult result = moderation.decide(s.getId(), Verdict.YES, ADMIN1);

        Artist saved = artists.findById(result.artist().getId()).orElseThrow();
        assertThat(saved.getSlug()).isEqualTo("okean-elzy-2");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(reload(s).getStatus()).isEqualTo(SubmissionStatus.APPROVED);
    }

    @Test
    void failedSaveRevertsAndRemovesCopiedPhotos() {
        artists.save(Artist.builder().id(new ObjectId().toHexString()).slug("other").name("Інший").wikidataId("Q1")
                .status(ArtistStatus.PUBLISHED).build());
        Submission s = pending("Океан Ельзи");
        List<Artist> built = new ArrayList<>();
        doAnswer(inv -> {
            Artist created = (Artist) inv.callRealMethod();
            created.setWikidataId("Q1"); // not a slug conflict: no second attempt
            built.add(created);
            return created;
        }).when(artistFactory).create(any(Submission.class));

        assertThatThrownBy(() -> moderation.decide(s.getId(), Verdict.YES, ADMIN1))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(reload(s).getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(reload(s).getModerationLog().getLast().action()).isEqualTo(ModerationAction.FAILED);
        assertThat(artists.count()).isEqualTo(1);
        for (ImageAsset image : built.getFirst().getImages()) {
            assertThatThrownBy(() -> storage.get(image.key())).isInstanceOf(NoSuchKeyException.class);
            assertThatThrownBy(() -> storage.get(image.thumbKey())).isInstanceOf(NoSuchKeyException.class);
        }
    }
}
