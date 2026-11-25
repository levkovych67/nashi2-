package com.nashi.wiki;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistSource;
import com.nashi.enums.ArtistStatus;
import com.nashi.util.StringUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Imports artists from Wikipedia that are not in the database yet. Imported artists are approved at once; the import
 * never posts to the channel and sends one report per run to the admin chat.
 */
@Slf4j
@Service
public class ArtistImportService {

    /** Details are fetched and saved in chunks, so a failure late in a run keeps what was saved before it. */
    static final int CHUNK = 200;
    private static final int FAILURE_TEXT_MAX = 300;

    private final CandidateSourceFactory sources;
    private final ArtistStore store;
    private final AdminNotifier notifier;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final int limitPerCategory;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param limitPerCategory at most this many new candidates of each {@link CandidateGroup} per run; 0 for all.
     *                         Meant for seeding a local database.
     */
    @Autowired
    public ArtistImportService(CandidateSourceFactory sources, ArtistStore store, AdminNotifier notifier,
                               @Value("${nashi.wiki-import.limit-per-category:0}") int limitPerCategory) {
        this(sources, store, notifier, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wiki-import");
            thread.setDaemon(true);
            return thread;
        }), limitPerCategory);
    }

    ArtistImportService(CandidateSourceFactory sources, ArtistStore store, AdminNotifier notifier, Executor executor) {
        this(sources, store, notifier, executor, 0);
    }

    ArtistImportService(CandidateSourceFactory sources, ArtistStore store, AdminNotifier notifier, Executor executor,
                        int limitPerCategory) {
        this.sources = sources;
        this.store = store;
        this.notifier = notifier;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService service ? service : null;
        this.limitPerCategory = limitPerCategory;
    }

    @EventListener
    public void onRequested(WikiImportRequested event) {
        startInBackground();
    }

    /** Starts a run unless one is going; tells the admin chat either way. */
    public boolean startInBackground() {
        if (!running.compareAndSet(false, true)) {
            notifier.send("Імпорт уже йде.");
            return false;
        }
        notifier.send("Імпорт з Вікіпедії запущено.");
        try {
            executor.execute(() -> {
                try {
                    runNow();
                } finally {
                    running.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            running.set(false);
            notifier.send("Імпорт з Вікіпедії не запустився: " + e.getMessage());
            return false;
        }
        return true;
    }

    /** One full run; always ends with a report to the admin chat. */
    public ImportReport runNow() {
        int candidates = 0;
        int alreadyKnown = 0;
        int added = 0;
        int withoutPlace = 0;
        int errors = 0;
        int failedCandidateChunks = 0;
        int russiaLinked = 0;
        String failure = null;

        ArtistCandidateSource source = null;
        try {
            source = sources.create();
            KnownArtists known = store.loadKnown();
            List<WikiCandidate> all = source.listCandidates();
            candidates = all.size();
            failedCandidateChunks = source.failedCandidateChunks();

            List<WikiCandidate> fresh = all.stream()
                    .filter(c -> !known.wikidataIds().contains(c.qid()) && !known.wikiTitles().contains(c.ukTitle()))
                    .toList();
            alreadyKnown = candidates - fresh.size();
            log.info("[ASYNC] [WIKI] {} candidates, {} already known", candidates, alreadyKnown);
            if (limitPerCategory > 0) {
                fresh = WikiCandidate.limitPerGroup(fresh, limitPerCategory);
                log.info("[ASYNC] [WIKI] {} new candidates kept, at most {} per group", fresh.size(), limitPerCategory);
            }

            Set<String> usedSlugs = new HashSet<>();
            for (List<WikiCandidate> chunk : WikiCollector.batches(fresh, CHUNK)) {
                for (WikiArtistData data : source.fetchDetails(chunk)) {
                    if (known.manualNames().contains(NameNormalizer.normalize(data.name()))) {
                        alreadyKnown++;
                        continue;
                    }
                    Artist artist = toArtist(data, slugFor(data, usedSlugs));
                    try {
                        store.save(artist);
                        added++;
                        if (artist.getLocation() == null) {
                            withoutPlace++;
                        }
                    } catch (RuntimeException e) {
                        errors++;
                        log.warn("[ASYNC] [WIKI] could not save {} ({}): {}", data.name(), data.qid(), e.getMessage());
                    }
                }
                log.info("[ASYNC] [WIKI] saved {} so far", added);
            }
        } catch (RuntimeException e) {
            failure = shorten(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            log.error("[ASYNC] [WIKI] import failed", e);
        }
        if (source != null) {
            russiaLinked = source.russiaLinkedExcluded();
        }

        ImportReport report = new ImportReport(candidates, alreadyKnown, added, withoutPlace, errors,
                failedCandidateChunks, russiaLinked, failure);
        // The report also goes to the log: with Telegram off (the local profile) this is the only place it shows.
        log.info("[ASYNC] [WIKI] {}", report.toTelegramText());
        notifier.send(report.toTelegramText());
        return report;
    }

    private String slugFor(WikiArtistData data, Set<String> usedSlugs) {
        String base = StringUtils.toSlug(data.name());
        if (base.isBlank()) {
            base = "artist";
        }
        String slug = base;
        if (usedSlugs.contains(slug) || store.slugExists(slug)) {
            slug = base + "-q" + data.qid().replaceAll("\\D", "");
        }
        usedSlugs.add(slug);
        return slug;
    }

    static Artist toArtist(WikiArtistData data, String slug) {
        WikiPlace place = data.place();
        return Artist.builder()
                .name(data.name())
                .slug(slug)
                .wikidataId(data.qid())
                .wikiTitle(data.wikiTitle())
                .source(ArtistSource.WIKIPEDIA)
                .status(ArtistStatus.APPROVED)
                .biography(data.biography())
                .style(data.style())
                .musicTags(new HashSet<>(data.tags()))
                .foundationYear(data.foundationYear())
                .disbandYear(data.disbandYear())
                .region(place != null ? place.region() : null)
                .city(place != null ? place.labelUk() : null)
                .location(place != null ? new GeoJsonPoint(place.lon(), place.lat()) : null)
                .imageKeys(data.imageUrl() != null ? new ArrayList<>(List.of(data.imageUrl())) : new ArrayList<>())
                .imageCredit(data.imageCredit())
                .socialLinks(new ArrayList<>(data.socialLinks()))
                .categories(new ArrayList<>(data.categories()))
                .birthDate(data.birthDate())
                .deathDate(data.deathDate())
                .birthPlace(data.birthPlace())
                .locationBasis(data.locationBasis())
                .genres(new ArrayList<>(data.genres()))
                .works(new ArrayList<>(data.works()))
                .build();
    }

    private static String shorten(String text) {
        return text.length() > FAILURE_TEXT_MAX ? text.substring(0, FAILURE_TEXT_MAX) + "…" : text;
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }
}
