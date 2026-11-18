package com.nashi.wiki;

import com.nashi.admin.JobTrigger;
import com.nashi.artist.Artist;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.Names;
import com.nashi.artist.SlugAllocator;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRunLog;
import com.nashi.place.Place;
import com.nashi.place.PlaceStore;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Imports musicians and bands from Wikipedia that are not in the database yet, published at once. One run at a time,
 * on {@code jobsExecutor}; each run is logged in {@code jobs_runs} ({@link JobKind#WIKI_IMPORT}) and its report goes
 * to the moderation group. A run that fails is recorded and announced by {@link JobRunLog} alone; artists saved
 * before the failure stay. Started by {@code /wiki_import} (this is the {@code wikiImport} {@link JobTrigger}),
 * {@link WikiImportScheduler}, {@link WikiImportRequested}, or once at startup with
 * {@code nashi.wiki-import.on-startup=true} while no artist from Wikipedia exists (for seeding a local database).
 */
@Service
@Qualifier("wikiImport")
public class MusicianImportService implements JobTrigger {

    private static final Logger log = LoggerFactory.getLogger(MusicianImportService.class);

    /** Details are fetched and saved in chunks, so a failure late in a run keeps what was saved before it. */
    static final int CHUNK = 200;

    private final CandidateSourceFactory sources;
    private final ArtistStore store;
    private final PlaceStore places;
    private final CommonsThumbnails thumbnails;
    private final AdminNotifier notifier;
    private final JobRunLog runLog;
    private final Executor executor;
    private final Clock clock;
    private final int limitPerGroup;
    private final boolean onStartup;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param limitPerGroup at most this many new candidates of each {@link CandidateGroup} per run; 0 for all. Meant
     *                      for seeding a local database.
     * @param onStartup     import once at startup when there is no artist from Wikipedia yet
     */
    public MusicianImportService(CandidateSourceFactory sources, ArtistStore store, PlaceStore places,
                                 CommonsThumbnails thumbnails, AdminNotifier notifier, JobRunLog runLog,
                                 @Qualifier("jobsExecutor") Executor executor, Clock clock,
                                 @Value("${nashi.wiki-import.limit-per-group:0}") int limitPerGroup,
                                 @Value("${nashi.wiki-import.on-startup:false}") boolean onStartup) {
        this.sources = sources;
        this.store = store;
        this.places = places;
        this.thumbnails = thumbnails;
        this.notifier = notifier;
        this.runLog = runLog;
        this.executor = executor;
        this.clock = clock;
        this.limitPerGroup = limitPerGroup;
        this.onStartup = onStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!onStartup) {
            return;
        }
        long imported = store.countWikipediaArtists();
        if (imported > 0) {
            log.info("[WIKI] {} artists from Wikipedia already; no import at startup", imported);
            return;
        }
        log.info("[WIKI] no artist from Wikipedia yet; importing in the background (nashi.wiki-import.on-startup)");
        startInBackground();
    }

    @EventListener
    public void onRequested(WikiImportRequested event) {
        startInBackground();
    }

    /** Queues a run on {@code jobsExecutor}; false when one is queued or running, or the queue is full. */
    @Override
    public boolean startInBackground() {
        if (!running.compareAndSet(false, true)) {
            log.info("[WIKI] an import is already running");
            return false;
        }
        // jobsExecutor drops a task silently when its queue is full; the flag would then never be cleared
        if (executor instanceof ThreadPoolTaskExecutor pool
                && pool.getThreadPoolExecutor().getQueue().remainingCapacity() == 0) {
            running.set(false);
            log.warn("[WIKI] the jobs queue is full; the import was not started");
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runNow();
                } catch (RuntimeException e) {
                    // JobRunLog has recorded, logged and announced the failure
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            running.set(false);
            log.warn("[WIKI] the import could not be queued: {}", e.getMessage());
            return false;
        }
    }

    /**
     * One full run in this thread; its report goes to {@code jobs_runs} and the group.
     *
     * @throws RuntimeException when the run stops (Wikidata down, the database gone); already recorded as FAILED
     */
    public ImportReport runNow() {
        AtomicReference<ImportReport> result = new AtomicReference<>();
        runLog.run(JobKind.WIKI_IMPORT, () -> {
            result.set(importNew());
            return result.get().toMap();
        });
        ImportReport report = result.get();
        // also in the log: with Telegram off (local and test) this is the only place it shows
        log.info("[WIKI] {}", report.toTelegramText());
        try {
            notifier.send(report.toTelegramText());
        } catch (RuntimeException e) {
            log.warn("[WIKI] the import report could not be sent: {}", e.getMessage());
        }
        return report;
    }

    private ImportReport importNew() {
        ArtistCandidateSource source = sources.create();
        KnownArtists known = store.loadKnown();
        List<WikiCandidate> all = source.listCandidates();
        List<WikiCandidate> fresh = all.stream()
                .filter(c -> !known.wikidataIds().contains(c.qid()) && !known.wikiTitles().contains(c.ukTitle()))
                .toList();
        int alreadyKnown = all.size() - fresh.size();
        log.info("[WIKI] {} candidates, {} already known", all.size(), alreadyKnown);
        if (limitPerGroup > 0) {
            fresh = WikiCandidate.limitPerGroup(fresh, limitPerGroup);
            log.info("[WIKI] {} new candidates kept, at most {} per group", fresh.size(), limitPerGroup);
        }

        int added = 0;
        int withoutPlace = 0;
        int errors = 0;
        Map<String, Place> placeCache = new HashMap<>();
        try {
            for (List<WikiCandidate> chunk : WikiCollector.batches(fresh, CHUNK)) {
                for (WikiArtistData data : source.fetchDetails(chunk)) {
                    if (known.manualNames().contains(Names.normalize(data.name()))) {
                        alreadyKnown++;
                        continue;
                    }
                    try {
                        Artist artist = importOne(data, placeCache);
                        added++;
                        if (artist.getPin() == null) {
                            withoutPlace++;
                        }
                    } catch (RuntimeException e) {
                        errors++;
                        log.warn("[WIKI] could not save {} ({}): {}", data.name(), data.qid(), e.getMessage());
                    }
                }
                log.info("[WIKI] saved {} so far", added);
            }
        } catch (RuntimeException e) {
            log.warn("[WIKI] the import stopped after adding {} artists", added);
            throw e;
        }
        return new ImportReport(all.size(), alreadyKnown, added, withoutPlace, errors,
                source.failedCandidateChunks(), source.russiaLinkedExcluded());
    }

    /** Maps, copies the thumbnail and saves one artist; a thumbnail stored for an artist that fails to save is removed. */
    private Artist importOne(WikiArtistData data, Map<String, Place> placeCache) {
        String id = new ObjectId().toHexString();
        WikiPlace origin = WikiMusicianMapper.originPlace(data);
        Place place = origin == null ? null : placeCache.computeIfAbsent(origin.qid(), qid -> place(origin));
        Artist artist = WikiMusicianMapper.toArtist(data, place,
                SlugAllocator.allocate(data.name(), store::slugExists), id);
        artist.setPublishedAt(clock.instant());
        String thumbKey = storeThumbnail(artist);
        try {
            return store.save(artist);
        } catch (RuntimeException e) {
            if (thumbKey != null) {
                thumbnails.delete(thumbKey);
            }
            throw e;
        }
    }

    /** The gazetteer place; one the seed does not have is added from what Wikidata says. */
    private Place place(WikiPlace p) {
        return places.find(p.qid()).orElseGet(() -> places.ensure(p.qid(), p.labelUk(), p.region(), p.lat(), p.lon()));
    }

    /** Puts our copy of the Commons thumbnail as {@code thumbKey}; returns its key, null when it stays external. */
    private String storeThumbnail(Artist artist) {
        if (artist.getImages() == null || artist.getImages().isEmpty()) {
            return null;
        }
        ImageAsset photo = artist.getImages().get(0);
        String key = photo.thumbUrl() == null ? null : thumbnails.store(photo.thumbUrl(), artist.getId());
        if (key != null) {
            List<ImageAsset> images = new ArrayList<>(artist.getImages());
            images.set(0, new ImageAsset(photo.key(), photo.url(), key, photo.thumbUrl(), photo.alt(), photo.credit()));
            artist.setImages(List.copyOf(images));
        }
        return key;
    }
}
