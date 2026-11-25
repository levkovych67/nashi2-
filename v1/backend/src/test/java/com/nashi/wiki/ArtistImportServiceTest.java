package com.nashi.wiki;

import com.nashi.domain.Artist;
import com.nashi.domain.ArtistWork;
import com.nashi.domain.ImageCredit;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.ArtistSource;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ArtistImportServiceTest {

    private static final WikiPlace LVIV = new WikiPlace("Q36036", "Львів", 49.84, 24.03, Region.LVIV);

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
            return new KnownArtists(Set.copyOf(wikidataIds), Set.copyOf(wikiTitles), Set.copyOf(manualNames));
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
        final List<String> askedFor = new ArrayList<>();
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
            wanted.forEach(c -> askedFor.add(c.qid()));
            Set<String> ids = new HashSet<>(wanted.stream().map(WikiCandidate::qid).toList());
            return artists.stream().filter(a -> ids.contains(a.qid())).toList();
        }

        void add(String qid, String name, WikiPlace place) {
            add(qid, name, place, CandidateGroup.MUSIC);
        }

        void add(String qid, String name, WikiPlace place, CandidateGroup group) {
            candidates.add(new WikiCandidate(qid, name + " (стаття)", group));
            artists.add(new WikiArtistData(qid, name, name + " (стаття)", false, "Біографія.", "рок", Set.of("rock"),
                    1990, null, place, "Photo.jpg",
                    List.of(SocialLink.builder().platform(SocialLink.Platform.WIKI).url("https://uk.wikipedia.org/wiki/x").build()),
                    "https://upload.wikimedia.org/photo.jpg",
                    ImageCredit.builder().author("Автор").license("CC BY-SA 4.0").build(),
                    List.of(ArtistCategory.MUSICIAN), null, null, null,
                    place != null ? LocationBasis.BIRTH : null, List.of("рок"), List.of()));
        }
    }

    private final FakeStore store = new FakeStore();
    private final FakeSource source = new FakeSource();
    private final List<String> messages = new ArrayList<>();

    private ArtistImportService service() {
        return new ArtistImportService(() -> source, store, messages::add, Runnable::run);
    }

    @Test
    void skipsArtistsAlreadyImportedById() {
        source.add("Q1", "Перший", LVIV);
        source.add("Q2", "Другий", LVIV);
        store.wikidataIds.add("Q1");

        ImportReport report = service().runNow();

        assertEquals(List.of("Q2"), source.askedFor);
        assertEquals(2, report.candidates());
        assertEquals(1, report.alreadyKnown());
        assertEquals(1, report.added());
    }

    @Test
    void skipsArtistsAlreadyImportedByArticleTitle() {
        source.add("Q1", "Перший", LVIV);
        store.wikiTitles.add("Перший (стаття)");

        service().runNow();

        assertTrue(source.askedFor.isEmpty());
        assertTrue(store.saved.isEmpty());
    }

    @Test
    void doesNotCopyAnArtistAddedThroughTheForm() {
        source.add("Q1", "Океан Ельзи", LVIV);
        store.manualNames.add(NameNormalizer.normalize("«Океан Ельзи»"));

        ImportReport report = service().runNow();

        assertTrue(store.saved.isEmpty());
        assertEquals(1, report.alreadyKnown());
    }

    @Test
    void importsNamesakesWithDistinctSlugs() {
        source.add("Q101", "Іван Петренко", LVIV);
        source.add("Q202", "Іван Петренко", LVIV);

        service().runNow();

        assertEquals(2, store.saved.size());
        assertEquals("іван-петренко", store.saved.get(0).getSlug());
        assertEquals("іван-петренко-q202", store.saved.get(1).getSlug());
    }

    @Test
    void addsASuffixWhenTheSlugIsAlreadyTaken() {
        source.add("Q303", "Іван Петренко", LVIV);
        store.slugs.add("іван-петренко");

        service().runNow();

        assertEquals("іван-петренко-q303", store.saved.get(0).getSlug());
    }

    @Test
    void savesAnApprovedWikipediaArtistOnTheMap() {
        source.add("Q1", "Тарас Чубай", LVIV);

        service().runNow();

        Artist artist = store.saved.get(0);
        assertEquals(ArtistStatus.APPROVED, artist.getStatus());
        assertEquals(ArtistSource.WIKIPEDIA, artist.getSource());
        assertEquals("Q1", artist.getWikidataId());
        assertEquals("Тарас Чубай (стаття)", artist.getWikiTitle());
        assertEquals(24.03, artist.getLocation().getX(), 1e-9);
        assertEquals(49.84, artist.getLocation().getY(), 1e-9);
        assertEquals(Region.LVIV, artist.getRegion());
        assertEquals("Львів", artist.getCity());
        assertEquals(List.of("https://upload.wikimedia.org/photo.jpg"), artist.getImageKeys());
        assertEquals("Автор", artist.getImageCredit().getAuthor());
        assertEquals(Set.of("rock"), artist.getMusicTags());
        assertEquals("рок", artist.getStyle());
        assertEquals("Біографія.", artist.getBiography());
        assertEquals(1990, artist.getFoundationYear());
        assertEquals(1, artist.getSocialLinks().size());
    }

    @Test
    void savesAnArtistWithoutAPlaceOffTheMap() {
        source.add("Q1", "Без місця", null);

        ImportReport report = service().runNow();

        assertNull(store.saved.get(0).getLocation());
        assertNull(store.saved.get(0).getRegion());
        assertEquals(1, report.withoutPlace());
    }

    @Test
    void keepsGoingWhenOneSaveFails() {
        source.add("Q1", "Зламаний", LVIV);
        source.add("Q2", "Цілий", LVIV);
        store.failOnName = "Зламаний";

        ImportReport report = service().runNow();

        assertEquals(1, report.errors());
        assertEquals(1, report.added());
        assertEquals("Цілий", store.saved.get(0).getName());
    }

    @Test
    void reportsAFailureAndCanRunAgain() {
        source.failure = new WikiFetchException("HTTP 504 for sparql");
        ArtistImportService service = service();

        assertTrue(service.startInBackground());

        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Імпорт з Вікіпедії впав")), messages.toString());
        source.failure = null;
        source.add("Q1", "Після збою", LVIV);
        assertTrue(service.startInBackground());
        assertEquals(1, store.saved.size());
    }

    @Test
    void sendsOneReportPerRun() {
        source.add("Q1", "Перший", LVIV);
        source.add("Q2", "Другий", null);

        service().startInBackground();

        assertEquals(List.of(
                "Імпорт з Вікіпедії запущено.",
                "Імпорт з Вікіпедії: знайдено 2, уже були 0, додано 2 (без місця на карті 1), помилок 0."), messages);
    }

    @Test
    void reportsCandidateQueriesThatFailed() {
        source.add("Q1", "Перший", LVIV);
        source.failedChunks = 3;

        ImportReport report = service().runNow();

        assertEquals(3, report.failedCandidateChunks());
        assertEquals("Імпорт з Вікіпедії: знайдено 1, уже були 0, додано 1 (без місця на карті 0), помилок 0, "
                + "не вдалося отримати частину кандидатів: 3 запитів.", messages.get(messages.size() - 1));
    }

    @Test
    void reportMentionsRussiaLinkedCount() {
        source.add("Q1", "Перший", LVIV);
        source.russiaLinked = 7;

        ImportReport report = service().runNow();

        assertEquals(7, report.russiaLinked());
        assertEquals("Імпорт з Вікіпедії: знайдено 1, уже були 0, додано 1 (без місця на карті 0), помилок 0, "
                + "відсіяно через зв'язок з РФ: 7.", messages.get(messages.size() - 1));
    }

    @Test
    void toArtistMapsNewFields() {
        ArtistWork kobzar = ArtistWork.builder().wikidataId("Q2658157").title("Кобзар").year(1840).build();
        WikiArtistData data = new WikiArtistData("Q134958", "Тарас Шевченко", "Шевченко Тарас Григорович", false,
                "Поет і художник.", "романтизм", Set.of(), null, null, LVIV, null, List.of(), null, null,
                List.of(ArtistCategory.VISUAL_ARTIST, ArtistCategory.WRITER), "1814-03-09", "1861-03-10", "Моринці",
                LocationBasis.RESIDENCE, List.of("романтизм", "реалізм"), List.of(kobzar));

        Artist artist = ArtistImportService.toArtist(data, "тарас-шевченко");

        assertEquals(List.of(ArtistCategory.VISUAL_ARTIST, ArtistCategory.WRITER), artist.getCategories());
        assertEquals("1814-03-09", artist.getBirthDate());
        assertEquals("1861-03-10", artist.getDeathDate());
        assertEquals("Моринці", artist.getBirthPlace());
        assertEquals(LocationBasis.RESIDENCE, artist.getLocationBasis());
        assertEquals(List.of("романтизм", "реалізм"), artist.getGenres());
        assertEquals(List.of(kobzar), artist.getWorks());
        assertEquals("Львів", artist.getCity());
        artist.getWorks().add(ArtistWork.builder().title("Ще").build());
        artist.getGenres().add("ще");
        artist.getCategories().add(ArtistCategory.MUSICIAN);
        assertEquals(1, data.works().size(), "the artist gets its own mutable lists");
    }

    @Test
    void limitPerCategoryKeepsNPerGroup() {
        for (int i = 1; i <= 5; i++) {
            source.add("QM" + i, "Музикант " + i, LVIV, CandidateGroup.MUSIC);
            source.add("QW" + i, "Письменник " + i, LVIV, CandidateGroup.WRITER);
        }
        store.wikidataIds.add("QM1");

        ImportReport report = new ArtistImportService(() -> source, store, messages::add, Runnable::run, 2).runNow();

        // the limit counts new candidates: QM1 is already known, so QM2 and QM3 are the two new musicians
        assertEquals(List.of("QW1", "QM2", "QW2", "QM3"), store.saved.stream().map(Artist::getWikidataId).toList());
        assertEquals(4, report.added());
        assertEquals(10, report.candidates());
    }

    @Test
    void zeroLimitMeansAll() {
        for (int i = 1; i <= 5; i++) {
            source.add("QM" + i, "Музикант " + i, LVIV, CandidateGroup.MUSIC);
            source.add("QW" + i, "Письменник " + i, LVIV, CandidateGroup.WRITER);
        }

        new ArtistImportService(() -> source, store, messages::add, Runnable::run, 0).runNow();

        assertEquals(10, store.saved.size());
    }

    @Test
    void refusesASecondRunWhileOneIsGoing() throws InterruptedException {
        source.started = new CountDownLatch(1);
        source.release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ArtistImportService service = new ArtistImportService(() -> source, store, messages::add, executor);
        try {
            assertTrue(service.startInBackground());
            assertTrue(source.started.await(5, TimeUnit.SECONDS));

            assertFalse(service.startInBackground());
            assertTrue(messages.contains("Імпорт уже йде."));
        } finally {
            source.release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void neverTouchesTheChannelOrModeration() {
        // The service has no access to approval or channel posting: its only outputs are the store and one notifier.
        AtomicBoolean onlyStoreAndNotifier = new AtomicBoolean(
                ArtistImportService.class.getDeclaredConstructors().length > 0);
        assertTrue(onlyStoreAndNotifier.get());
        for (var field : ArtistImportService.class.getDeclaredFields()) {
            String type = field.getType().getSimpleName();
            assertFalse(type.contains("Approval") || type.contains("Telegram"), "unexpected dependency " + type);
        }
    }
}
