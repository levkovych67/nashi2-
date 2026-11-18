package com.nashi.audio;

import com.nashi.admin.JobTrigger;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRunLog;
import com.nashi.moderation.AudioResolveRequested;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Looks for preview clips of published musicians: every night at 03:00 Kyiv time for those never checked or checked
 * more than 30 days ago, one after another, and at once for one musician after an approval
 * ({@link AudioResolveRequested}, on {@code jobsExecutor}, never in the Telegram callback thread). A night run is
 * logged in {@code jobs_runs} ({@link JobKind#AUDIO_RESOLVE}); only one runs at a time.
 * <p>
 * Results are saved with a targeted {@code $set} (driver call, no version increment) of {@code audio} and {@code audioCheckedAt} (plus {@code updatedAt}
 * when the clips changed, which moves the map version and the page validators), never a full save, so likes and
 * moderation edits do not clash with it over {@code @Version}. {@link ArtistChangedEvent} is published only when
 * {@link AudioPolicy#hasAudio} flipped.
 */
@Component
@Qualifier("audioResolve")
public class AudioResolveJob implements JobTrigger {

    static final Duration RECHECK_AFTER = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(AudioResolveJob.class);

    private final MongoTemplate mongo;
    private final JobRunLog runLog;
    private final AudioResolver resolver;
    private final AudioPolicy policy;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AudioResolveJob(MongoTemplate mongo, JobRunLog runLog, AudioResolver resolver, AudioPolicy policy,
                           ApplicationEventPublisher events, Clock clock, @Qualifier("jobsExecutor") Executor executor) {
        this.mongo = mongo;
        this.runLog = runLog;
        this.resolver = resolver;
        this.policy = policy;
        this.events = events;
        this.clock = clock;
        this.executor = executor;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Kyiv")
    public void nightly() {
        startInBackground();
    }

    /** Queues a run on {@code jobsExecutor}; false when one is queued or running, or the queue is full. */
    @Override
    public boolean startInBackground() {
        if (!running.compareAndSet(false, true)) {
            log.info("[AUDIO] a run is already in progress");
            return false;
        }
        // jobsExecutor drops a task silently when its queue is full; the flag would then never be cleared
        if (executor instanceof ThreadPoolTaskExecutor pool
                && pool.getThreadPoolExecutor().getQueue().remainingCapacity() == 0) {
            running.set(false);
            log.warn("[AUDIO] the jobs queue is full; the run was not started");
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runAll();
                } catch (RuntimeException e) {
                    // JobRunLog has recorded, logged and announced the failure
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            running.set(false);
            log.warn("[AUDIO] the run could not be queued: {}", e.getMessage());
            return false;
        }
    }

    /**
     * One full pass in this thread, recorded in {@code jobs_runs}.
     *
     * @return the report: {@code checked}, {@code failed}, {@code withAudioBefore} and {@code withAudioAfter} (among
     *         the musicians checked in this run) and {@code bySource}, the clips by source after the run
     */
    public Map<String, Object> runAll() {
        return runLog.run(JobKind.AUDIO_RESOLVE, this::resolveDue);
    }

    @Async("jobsExecutor")
    @EventListener
    public void onRequested(AudioResolveRequested event) {
        try {
            Artist artist = mongo.findById(event.artistId(), Artist.class);
            if (artist == null) {
                log.warn("[AUDIO] artist {} not found", event.artistId());
                return;
            }
            resolveOne(artist);
        } catch (RuntimeException e) {
            log.warn("[AUDIO] could not resolve artist {}: {}", event.artistId(), e.getMessage());
        }
    }

    private Map<String, Object> resolveDue() {
        Instant now = clock.instant();
        Query due = Query.query(Criteria.where("status").is(ArtistStatus.PUBLISHED).orOperator(
                Criteria.where("audioCheckedAt").is(null),
                Criteria.where("audioCheckedAt").lt(now.minus(RECHECK_AFTER))));
        due.fields().include("_id");
        // raw documents: a projection to _id alone cannot be mapped to Artist (its likesCount is a primitive)
        List<String> ids = mongo.find(due, org.bson.Document.class, "artists").stream()
                .map(d -> String.valueOf(d.get("_id"))).sorted().toList();
        log.info("[AUDIO] {} musicians to check", ids.size());

        int before = 0;
        int after = 0;
        int failed = 0;
        Map<String, Integer> bySource = new TreeMap<>();
        for (String id : ids) {
            try {
                Artist artist = mongo.findById(id, Artist.class);
                if (artist == null) {
                    continue;
                }
                boolean hadAudio = policy.hasAudio(artist);
                List<AudioClip> clips = resolveOne(artist);
                before += hadAudio ? 1 : 0;
                after += policy.hasAudio(artist) ? 1 : 0;
                clips.forEach(c -> bySource.merge(c.source().name(), 1, Integer::sum));
            } catch (RuntimeException e) {
                failed++;
                log.warn("[AUDIO] could not resolve artist {}: {}", id, e.getMessage());
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checked", ids.size() - failed);
        report.put("failed", failed);
        report.put("withAudioBefore", before);
        report.put("withAudioAfter", after);
        report.put("bySource", bySource);
        log.info("[AUDIO] {}", report);
        return report;
    }

    private Document toDocument(AudioClip clip) {
        Document doc = new Document();
        mongo.getConverter().write(clip, doc);
        doc.remove("_class");
        return doc;
    }

    /** Resolves one musician and saves; {@code artist} is updated in place. Returns the new clip list. */
    private List<AudioClip> resolveOne(Artist artist) {
        boolean hadAudio = policy.hasAudio(artist);
        List<AudioClip> before = artist.getAudio() == null ? List.of() : artist.getAudio();
        List<AudioClip> clips = resolver.resolve(artist);
        Instant now = clock.instant();

        List<Document> docs = clips.stream().map(this::toDocument).toList();
        List<Bson> changes = new ArrayList<>(List.of(Updates.set("audio", docs),
                Updates.set("audioCheckedAt", Date.from(now))));
        if (!clips.equals(before)) {
            changes.add(Updates.set("updatedAt", Date.from(now)));
        }
        // the driver, not MongoTemplate.updateFirst: for a versioned entity the template adds $inc on version
        Object id = ObjectId.isValid(artist.getId()) ? new ObjectId(artist.getId()) : artist.getId();
        mongo.getCollection("artists").updateOne(Filters.eq("_id", id), Updates.combine(changes));
        artist.setAudio(clips);
        artist.setAudioCheckedAt(now);

        if (hadAudio != policy.hasAudio(artist)) {
            events.publishEvent(new ArtistChangedEvent(artist.getId()));
        }
        return clips;
    }
}
