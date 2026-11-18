package com.nashi.wiki;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Discipline;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Names;
import com.nashi.artist.OriginBasis;
import com.nashi.geo.Region;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRunLog;
import com.nashi.media.FileStorage;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import com.nashi.place.PlaceType;
import com.nashi.telegram.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicianImportServiceTest {

    private static final WikiPlace LVIV = new WikiPlace("Q36036", "Львів", 49.84, 24.03, Region.LVIV);
    private static final WikiPlace KHUTIR = new WikiPlace("Q4149698", "Гриньки", 49.4575, 33.03, Region.POLTAVA);
    private static final Instant NOW = Instant.parse("2026-12-01T02:00:00Z");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10};

    /** In-memory store standing in for MongoDB. */
    static class FakeStore implements ArtistStore {
        final List<Artist> saved = new ArrayList<>();
        final Set<String> wikidataIds = new HashSet<>();
        final Set<String> wikiTitles = new HashSet<>();
        final Set<String> manualNames = new HashSet<>();
        final Set<String> slugs = new HashSet<>();
        String failOnName;

        @Override
        public KnownArtists loadKnown() {
            Set<String> ids = new HashSet<>(wikidataIds);
            Set<String> titles = new HashSet<>(wikiTitles);
            saved.forEach(a -> {
                ids.add(a.getWikidataId());
                titles.add(a.getWikiTitle());
            });
            return new KnownArtists(ids, titles, Set.copyOf(manualNames));
        }

        @Override
        public boolean slugExists(String slug) {
            return slugs.contains(slug);
        }

        @Override
        public long countWikipediaArtists() {
            return saved.stream().filter(a -> a.getSource() == ArtistSource.WIKIPEDIA).count();
        }

        @Override
        public Artist save(Artist artist) {
            if (artist.getName().equals(failOnName)) {
                throw new IllegalStateException("duplicate key");
            }
            saved.add(artist);
            slugs.add(artist.getSlug());
            return artist;
        }
    }

    /** Candidate source returning prepared artists; records what it was asked for. */
    static class FakeSource implements ArtistCandidateSource {
        final List<WikiCandidate> candidates = new ArrayList<>();
        final List<WikiArtistData> artists = new ArrayList<>();
        final List<WikiCandidate> askedFor = new ArrayList<>();
        RuntimeException failure;
        int failedChunks;
        int russiaLinked;
        CountDownLatch started;
        CountDownLatch release;

        @Override
        public List<WikiCandidate> listCandidates() {
            if (started != null) {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            return candidates;
        }

        @Override
        public int failedCandidateChunks() {
            return failedChunks;
        }

        @Override
        public int russiaLinkedExcluded() {
            return russiaLinked;
        }

        @Override
        public List<WikiArtistData> fetchDetails(List<WikiCandidate> wanted) {
            askedFor.addAll(wanted);
            Set<String> ids = new HashSet<>(wanted.stream().map(WikiCandidate::qid).toList());
            return artists.stream().filter(a -> ids.contains(a.qid())).toList();
        }

        void add(String qid, String name, WikiPlace place) {
            add(qid, name, place, CandidateGroup.MUSIC);
        }

        void add(String qid, String name, WikiPlace place, CandidateGroup group) {
            candidates.add(new WikiCandidate(qid, name + " (стаття)", group));
            boolean band = group == CandidateGroup.ENSEMBLE;
            artists.add((band ? TestArtists.band(qid, name) : TestArtists.musician(qid, name))
                    .wikiTitle(name + " (стаття)")
                    .biography(name + " (нар. 1970) — музикант.")
                    .place(place, place == null ? null : band ? LocationBasis.FORMATION : LocationBasis.BIRTH)
                    .photo(qid + ".jpg", "Автор", "CC BY-SA 4.0")
                    .link(WikiLink.Platform.WIKI, "https://uk.wikipedia.org/wiki/x")
                    .build());
        }
    }

    private final FakeStore store = new FakeStore();
    private final FakeSource source = new FakeSource();
    private final List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final PlaceStore places = mock(PlaceStore.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final JobRunLog runLog = mock(JobRunLog.class);
    private final List<String> downloads = new ArrayList<>();
    private WikiFileHttp files = url -> {
        downloads.add(url);
        return new WikiFileHttp.WikiFile(JPEG, "image/jpeg");
    };

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(runLog.run(any(), any())).thenAnswer(inv -> ((Supplier<Map<String, Object>>) inv.getArgument(1)).get());
        when(places.find(anyString())).thenReturn(Optional.empty());
        when(places.ensure(anyString(), anyString(), any(), anyDouble(), anyDouble())).thenAnswer(inv -> new Place(
                inv.getArgument(0), inv.getArgument(1), Names.normalize(inv.getArgument(1)), PlaceType.OTHER,
                inv.getArgument(2), new GeoJsonPoint((double) inv.getArgument(4), (double) inv.getArgument(3)), null,
                null));
    }

    private MusicianImportService service() {
        return service(() -> source, Runnable::run, 0, false);
    }

    private MusicianImportService service(CandidateSourceFactory sources, java.util.concurrent.Executor executor,
                                          int limitPerGroup, boolean onStartup) {
        return new MusicianImportService(sources, store, places, new CommonsThumbnails(url -> files.getFile(url), storage),
                messages::add, runLog, executor, Clock.fixed(NOW, ZoneOffset.UTC), limitPerGroup, onStartup);
    }

    @Test
    void skipsArtistsAlreadyImportedById() {
        source.add("Q1", "Перший", LVIV);
        source.add("Q2", "Другий", LVIV);
        store.wikidataIds.add("Q1");

        ImportReport report = service().runNow();

        assertThat(source.askedFor).extracting(WikiCandidate::qid).containsExactly("Q2");
        assertThat(report.candidates()).isEqualTo(2);
        assertThat(report.alreadyKnown()).isEqualTo(1);
        assertThat(report.added()).isEqualTo(1);
    }

    @Test
    void skipsArtistsAlreadyImportedByArticleTitle() {
        source.add("Q1", "Перший", LVIV);
        store.wikiTitles.add("Перший (стаття)");

        service().runNow();

        assertThat(source.askedFor).isEmpty();
        assertThat(store.saved).isEmpty();
    }

    @Test
    void secondRunAddsNothing() {
        source.add("Q1", "Перший", LVIV);
        source.add("Q2", "Другий", null);

        ImportReport first = service().runNow();
        ImportReport second = service().runNow();

        assertThat(first.added()).isEqualTo(2);
        assertThat(second.added()).isZero();
        assertThat(second.alreadyKnown()).isEqualTo(2);
        assertThat(store.saved).hasSize(2);
    }

    @Test
    void skipsArtistAddedThroughFormWithSameName() {
        source.add("Q1", "Океан Ельзи", LVIV);
        store.manualNames.add(Names.normalize("«Океан Ельзи»"));

        ImportReport report = service().runNow();

        assertThat(store.saved).isEmpty();
        assertThat(report.alreadyKnown()).isEqualTo(1);
    }

    @Test
    void importsNamesakesWithDistinctSlugs() {
        source.add("Q101", "Іван Петренко", LVIV);
        source.add("Q202", "Іван Петренко", LVIV);

        service().runNow();

        assertThat(store.saved).extracting(Artist::getSlug).containsExactly("ivan-petrenko", "ivan-petrenko-2");
    }

    @Test
    void addsASuffixWhenTheSlugIsAlreadyTaken() {
        source.add("Q303", "Іван Петренко", LVIV);
        store.slugs.add("ivan-petrenko");

        service().runNow();

        assertThat(store.saved.get(0).getSlug()).isEqualTo("ivan-petrenko-2");
    }

    @Test
    void savesAPublishedWikipediaArtistOnTheMap() {
        when(places.find(LVIV.qid())).thenReturn(Optional.of(new Place(LVIV.qid(), "Львів", "львів", PlaceType.CITY,
                Region.LVIV, new GeoJsonPoint(24.03, 49.84), 182.0, 717_000L)));
        source.add("Q1", "Тарас Чубай", LVIV);

        service().runNow();

        Artist artist = store.saved.get(0);
        assertThat(artist.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(artist.getSource()).isEqualTo(ArtistSource.WIKIPEDIA);
        assertThat(artist.getDisciplines()).containsExactly(Discipline.MUSIC);
        assertThat(artist.getWikidataId()).isEqualTo("Q1");
        assertThat(artist.getWikiTitle()).isEqualTo("Тарас Чубай (стаття)");
        assertThat(artist.getSummary()).isEqualTo("Тарас Чубай — музикант.");
        assertThat(artist.getOrigin().placeId()).isEqualTo("Q36036");
        assertThat(artist.getOrigin().basis()).isEqualTo(OriginBasis.BIRTH);
        assertThat(artist.getPin()).isNotNull();
        assertThat(artist.getPublishedAt()).isEqualTo(NOW);
        assertThat(artist.getId()).matches("[0-9a-f]{24}");
        verify(places, never()).ensure(anyString(), anyString(), any(), anyDouble(), anyDouble());
    }

    @Test
    void unknownPlaceIsEnsuredInPlaces() {
        source.add("Q1", "Микола Лисенко", KHUTIR);

        service().runNow();

        verify(places).ensure("Q4149698", "Гриньки", Region.POLTAVA, 49.4575, 33.03);
        assertThat(store.saved.get(0).getOrigin().placeId()).isEqualTo("Q4149698");
        assertThat(store.saved.get(0).getOrigin().region()).isEqualTo(Region.POLTAVA);
    }

    @Test
    void looksEachPlaceUpOncePerRun() {
        source.add("Q1", "Перший", KHUTIR);
        source.add("Q2", "Другий", KHUTIR);

        service().runNow();

        verify(places).find("Q4149698");
        verify(places).ensure("Q4149698", "Гриньки", Region.POLTAVA, 49.4575, 33.03);
    }

    @Test
    void savesAnArtistWithoutAPlaceOffTheMap() {
        source.add("Q1", "Без місця", null);

        ImportReport report = service().runNow();

        assertThat(store.saved.get(0).getOrigin()).isNull();
        assertThat(store.saved.get(0).getPin()).isNull();
        assertThat(report.withoutPlace()).isEqualTo(1);
    }

    @Test
    void storesTheCommonsThumbnailAsOurThumbKey() {
        source.add("Q1", "Перший", LVIV);

        service().runNow();

        Artist artist = store.saved.get(0);
        String key = "artists/" + artist.getId() + "/0-thumb.jpg";
        verify(storage).put(key, JPEG, "image/jpeg");
        assertThat(downloads).containsExactly(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Q1.jpg/400px-Q1.jpg");
        ImageAsset image = artist.getImages().get(0);
        assertThat(image.thumbKey()).isEqualTo(key);
        assertThat(image.url()).isEqualTo("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Q1.jpg/800px-Q1.jpg");
        assertThat(image.thumbUrl()).isEqualTo(downloads.get(0));
        assertThat(image.key()).isNull();
        assertThat(image.credit().author()).isEqualTo("Автор");
    }

    @Test
    void keepsTheExternalThumbnailWhenTheDownloadFails() {
        files = url -> {
            throw new WikiFetchException("HTTP 404 for " + url, 404);
        };
        source.add("Q1", "Перший", LVIV);

        ImportReport report = service().runNow();

        ImageAsset image = store.saved.get(0).getImages().get(0);
        assertThat(image.thumbKey()).isNull();
        assertThat(image.thumbUrl()).endsWith("/400px-Q1.jpg");
        assertThat(report.added()).isEqualTo(1);
        assertThat(report.errors()).isZero();
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void keepsTheExternalThumbnailWhenTheAnswerIsNoPhoto() {
        files = url -> new WikiFileHttp.WikiFile("<svg/>".getBytes(), "image/svg+xml");
        source.add("Q1", "Перший", LVIV);

        service().runNow();

        assertThat(store.saved.get(0).getImages().get(0).thumbKey()).isNull();
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void keepsTheExternalThumbnailWhenStorageFails() {
        doThrow(new IllegalStateException("bucket down")).when(storage).put(anyString(), any(), anyString());
        source.add("Q1", "Перший", LVIV);

        ImportReport report = service().runNow();

        assertThat(store.saved.get(0).getImages().get(0).thumbKey()).isNull();
        assertThat(report.added()).isEqualTo(1);
    }

    @Test
    void keepsGoingWhenOneSaveFailsAndRemovesItsThumbnail() {
        source.add("Q1", "Зламаний", LVIV);
        source.add("Q2", "Цілий", LVIV);
        store.failOnName = "Зламаний";

        ImportReport report = service().runNow();

        assertThat(report.errors()).isEqualTo(1);
        assertThat(report.added()).isEqualTo(1);
        assertThat(store.saved).extracting(Artist::getName).containsExactly("Цілий");
        verify(storage).delete(any());
    }

    @Test
    void runsThroughTheJobLogAndSendsOneReport() {
        source.add("Q1", "Перший", LVIV);
        source.add("Q2", "Другий", null);

        service().runNow();

        verify(runLog).run(eq(JobKind.WIKI_IMPORT), any());
        assertThat(messages).containsExactly(
                "Імпорт з Вікіпедії: знайдено 2, уже були 0, додано 2 (без місця на карті 1), помилок 0.");
    }

    @Test
    void theReportGoesToJobsRuns() {
        source.add("Q1", "Перший", LVIV);
        source.failedChunks = 3;
        source.russiaLinked = 7;
        List<Map<String, Object>> reports = new ArrayList<>();
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> report = ((Supplier<Map<String, Object>>) inv.getArgument(1)).get();
            reports.add(report);
            return report;
        }).when(runLog).run(eq(JobKind.WIKI_IMPORT), any());

        service().runNow();

        assertThat(reports).containsExactly(Map.of("candidates", 1, "alreadyKnown", 0, "added", 1, "withoutPlace", 0,
                "errors", 0, "failedCandidateChunks", 3, "russiaLinked", 7));
        assertThat(messages.get(0)).isEqualTo("Імпорт з Вікіпедії: знайдено 1, уже були 0, додано 1 "
                + "(без місця на карті 0), помилок 0, відсіяно через зв'язок з РФ: 7, не вдалося отримати частину "
                + "кандидатів: 3 запитів.");
    }

    @Test
    void aFailedRunIsLeftToTheJobLogAndCanRunAgain() {
        source.failure = new WikiFetchException("HTTP 504 for sparql");
        MusicianImportService service = service();

        assertThatThrownBy(service::runNow).isInstanceOf(WikiFetchException.class);
        assertThat(service.startInBackground()).isTrue();

        assertThat(messages).as("the FAILED alert is the job log's alone").isEmpty();
        source.failure = null;
        source.add("Q1", "Після збою", LVIV);
        assertThat(service.startInBackground()).isTrue();
        assertThat(store.saved).hasSize(1);
    }

    @Test
    void limitPerGroupKeepsNPerGroup() {
        for (int i = 1; i <= 5; i++) {
            source.add("QM" + i, "Музикант " + i, LVIV, CandidateGroup.MUSIC);
            source.add("QE" + i, "Гурт " + i, LVIV, CandidateGroup.ENSEMBLE);
        }
        store.wikidataIds.add("QM1");

        ImportReport report = service(() -> source, Runnable::run, 2, false).runNow();

        // the limit counts new candidates: QM1 is already known, so QM2 and QM3 are the two new musicians
        assertThat(store.saved).extracting(Artist::getWikidataId).containsExactly("QE1", "QM2", "QE2", "QM3");
        assertThat(report.added()).isEqualTo(4);
        assertThat(report.candidates()).isEqualTo(10);
    }

    @Test
    void zeroLimitMeansAll() {
        for (int i = 1; i <= 5; i++) {
            source.add("QM" + i, "Музикант " + i, LVIV, CandidateGroup.MUSIC);
            source.add("QE" + i, "Гурт " + i, LVIV, CandidateGroup.ENSEMBLE);
        }

        service().runNow();

        assertThat(store.saved).hasSize(10);
    }

    @Test
    void refusesASecondRunWhileOneIsGoing() throws InterruptedException {
        source.started = new CountDownLatch(1);
        source.release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        MusicianImportService service = service(() -> source, executor, 0, false);
        try {
            assertThat(service.startInBackground()).isTrue();
            assertThat(source.started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(service.startInBackground()).isFalse();
        } finally {
            source.release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(messages).hasSize(1);
    }

    @Test
    void refusesWhenTheJobsQueueIsFullAndCanStartLater() throws InterruptedException {
        // the jobs executor drops a task when its queue is full, without telling the caller
        ThreadPoolTaskExecutor jobs = new ThreadPoolTaskExecutor();
        jobs.setCorePoolSize(1);
        jobs.setMaxPoolSize(1);
        jobs.setQueueCapacity(1);
        jobs.setRejectedExecutionHandler((task, pool) -> { });
        jobs.initialize();
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            jobs.execute(() -> {
                busy.countDown();
                awaitQuietly(release);
            });
            assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();
            jobs.execute(() -> { });
            source.add("Q1", "Перший", LVIV);
            MusicianImportService service = service(() -> source, jobs, 0, false);

            assertThat(service.startInBackground()).isFalse();

            release.countDown();
            awaitUntil(() -> jobs.getQueueSize() == 0);
            assertThat(service.startInBackground()).isTrue();
            awaitUntil(() -> messages.size() == 1);
            assertThat(store.saved).hasSize(1);
        } finally {
            release.countDown();
            jobs.shutdown();
        }
    }

    @Test
    void startsWhenAnImportIsRequested() {
        source.add("Q1", "Перший", LVIV);

        service().onRequested(new WikiImportRequested());

        assertThat(store.saved).hasSize(1);
    }

    @Test
    void importsOnStartupWhenAskedAndThereIsNoWikipediaArtistYet() {
        source.add("Q1", "Перший", LVIV);

        service(() -> source, Runnable::run, 0, true).onApplicationReady();

        assertThat(store.saved).hasSize(1);
    }

    @Test
    void doesNotImportOnStartupWhenAlreadySeededOrNotAsked() {
        store.saved.add(Artist.builder().name("Тарас Шевченко").slug("taras").source(ArtistSource.WIKIPEDIA).build());
        source.add("Q1", "Перший", LVIV);

        service(() -> source, Runnable::run, 0, true).onApplicationReady();
        service(() -> source, Runnable::run, 0, false).onApplicationReady();

        assertThat(store.saved).hasSize(1);
        assertThat(source.askedFor).isEmpty();
    }

    @Test
    void importsOnlyMusicGroups() {
        FakeWikimedia wikimedia = new FakeWikimedia(List.of("Q172425", "Лисенко Микола Віталійович"),
                List.of("Q1008489", "Океан Ельзи"));

        ImportReport report = service(() -> new WikiCollector(wikimedia), Runnable::run, 0, false).runNow();

        assertThat(CandidateGroup.values()).containsExactly(CandidateGroup.MUSIC, CandidateGroup.ENSEMBLE);
        assertThat(wikimedia.treeRoots()).as("only the musician trees; no painters or writers")
                .containsExactlyElementsOf(OccupationCategories.MUSICIAN_ROOTS);
        assertThat(report.added()).isEqualTo(2);
        assertThat(store.saved).extracting(Artist::getWikidataId).containsExactly("Q172425", "Q1008489");
        assertThat(store.saved).allSatisfy(a -> assertThat(a.getDisciplines()).containsExactly(Discipline.MUSIC));
        assertThat(store.saved.get(1).getWorks()).hasSize(12);
    }

    @Test
    void excludesRussiaLinked() {
        // Bogatikov holds the Russian Order of Friendship; Glière's titles are of the RSFSR and do not count
        FakeWikimedia wikimedia = new FakeWikimedia(
                List.of("Q4089030", "Богатиков Юрій Йосипович", "Q316795", "Глієр Рейнгольд Моріцевич"), List.of());

        ImportReport report = service(() -> new WikiCollector(wikimedia), Runnable::run, 0, false).runNow();

        assertThat(store.saved).extracting(Artist::getWikidataId).containsExactly("Q316795");
        assertThat(report.russiaLinked()).isEqualTo(1);
        assertThat(messages.get(0)).contains("відсіяно через зв'язок з РФ: 1");
    }

    @Test
    void reportGoesToTheGroupAsEscapedText() {
        TelegramGateway gateway = mock(TelegramGateway.class);

        new WikiImportConfig().wikiAdminNotifier(gateway).send("Імпорт: <b> & \"x\"");

        verify(gateway).sendText("Імпорт: &lt;b&gt; &amp; &quot;x&quot;");
    }

    @Test
    void neverTouchesModerationOrTelegramDirectly() {
        for (var field : MusicianImportService.class.getDeclaredFields()) {
            String type = field.getType().getSimpleName();
            assertThat(type).doesNotContain("Moderation", "Telegram", "Submission");
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
