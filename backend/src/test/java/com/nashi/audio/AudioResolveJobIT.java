package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.jobs.JobKind;
import com.nashi.jobs.JobRun;
import com.nashi.jobs.JobRunLog;
import com.nashi.moderation.AudioResolveRequested;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AudioResolveJobIT extends MongoIT {

    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private JobRunLog runLog;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private Clock clock;
    @Autowired
    private RecordedEvents recorded;

    private final AtomicInteger providerCalls = new AtomicInteger();
    private final AudioPolicy policy = AudioPolicy.of(true, true, true);
    /** Deezer finds one clip for every artist; the network is never touched. */
    private final AudioProvider deezer = new AudioProvider() {
        public AudioSource source() {
            return AudioSource.DEEZER;
        }

        public List<AudioClip> find(Artist artist) {
            providerCalls.incrementAndGet();
            return List.of(new AudioClip("dz_1", AudioSource.DEEZER, "1", null, null, "Song", 30, "Прев'ю: Deezer", null));
        }
    };
    private AudioResolveJob job;

    @BeforeEach
    void setUp() {
        artists.deleteAll();
        mongo.remove(new Query(), JobRun.class);
        recorded.clear();
        job = new AudioResolveJob(mongo, runLog, new AudioResolver(List.of(deezer), policy), policy, events, clock,
                Runnable::run);
    }

    @AfterEach
    void tearDown() {
        artists.deleteAll();
        mongo.remove(new Query(), JobRun.class);
        recorded.clear();
    }

    private Artist save(Instant checkedAt, List<AudioClip> audio) {
        return artists.save(Artist.builder().id(new ObjectId().toHexString()).slug("s" + System.nanoTime()).name("N")
                .status(ArtistStatus.PUBLISHED).audioCheckedAt(checkedAt).audio(audio).likesCount(0).build());
    }

    private static AudioClip upload() {
        return new AudioClip("up1", AudioSource.UPLOAD, null, "k", "u", "Mine", 20, "a", null);
    }

    @Test
    void jobSkipsRecentlyChecked() {
        Artist recent = save(clock.instant().minus(Duration.ofDays(5)), null);
        Artist never = save(null, null);
        Artist old = save(clock.instant().minus(Duration.ofDays(31)), null);

        job.runAll();

        assertThat(providerCalls).hasValue(2);
        assertThat(artists.findById(recent.getId()).orElseThrow().getAudio()).isNull();
        assertThat(artists.findById(never.getId()).orElseThrow().getAudio()).hasSize(1);
        assertThat(artists.findById(old.getId()).orElseThrow().getAudio()).hasSize(1);
    }

    @Test
    void jobIgnoresHiddenArtists() {
        artists.save(Artist.builder().id(new ObjectId().toHexString()).slug("h").name("H")
                .status(ArtistStatus.HIDDEN).likesCount(0).build());

        job.runAll();

        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void jobPublishesChangeWhenHasAudioFlips() {
        Artist flips = save(null, null);
        Artist stays = save(null, List.of(upload()));

        job.runAll();

        assertThat(recorded.artistEvents()).containsExactly(new ArtistChangedEvent(flips.getId()));
        assertThat(artists.findById(stays.getId()).orElseThrow().getAudio()).extracting(AudioClip::clipId)
                .containsExactly("up1", "dz_1");
    }

    @Test
    void savesWithATargetedSetAndKeepsUploads() {
        Artist a = save(null, List.of(upload()));
        long versionBefore = artists.findById(a.getId()).orElseThrow().getVersion();

        Instant started = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        job.runAll();

        Artist after = artists.findById(a.getId()).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(versionBefore);
        assertThat(after.getAudioCheckedAt()).isBetween(started, clock.instant());
        assertThat(after.getAudio()).extracting(AudioClip::clipId).containsExactly("up1", "dz_1");
        // the clip list changed, so the map version and the page's validators move on
        assertThat(after.getUpdatedAt()).isBetween(started, clock.instant());
    }

    @Test
    void anArtistThatFailsDoesNotStopTheRun() {
        AudioProvider flaky = new AudioProvider() {
            public AudioSource source() {
                return AudioSource.ITUNES;
            }

            public List<AudioClip> find(Artist artist) {
                throw new IllegalStateException("down");
            }
        };
        AudioResolveJob jobWithFlaky = new AudioResolveJob(mongo, runLog, new AudioResolver(List.of(deezer, flaky), policy),
                policy, events, clock, Runnable::run);
        save(null, null);

        jobWithFlaky.runAll();

        assertThat(artists.findAll().get(0).getAudio()).hasSize(1);
    }

    @Test
    void reportsBeforeAfterAndBySourceInTheRunLog() {
        save(null, null);
        save(null, List.of(upload()));

        Map<String, Object> report = job.runAll();

        assertThat(report).containsEntry("withAudioBefore", 1).containsEntry("withAudioAfter", 2);
        assertThat(report.get("bySource")).isEqualTo(Map.of("UPLOAD", 1, "DEEZER", 2));
        JobRun run = runLog.lastRun(JobKind.AUDIO_RESOLVE).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(JobRunLog.OK);
        assertThat(run.getReport()).containsEntry("withAudioAfter", 2);
    }

    @Test
    void requestResolvesOneMusicianAtOnceEvenIfCheckedRecently() {
        Artist recent = save(clock.instant().minus(Duration.ofDays(1)), null);
        Artist other = save(null, null);

        job.onRequested(new AudioResolveRequested(recent.getId()));

        assertThat(artists.findById(recent.getId()).orElseThrow().getAudio()).hasSize(1);
        assertThat(artists.findById(other.getId()).orElseThrow().getAudio()).isNull();
        assertThat(recorded.artistEvents()).containsExactly(new ArtistChangedEvent(recent.getId()));
    }

    @Test
    void requestForAnUnknownMusicianIsIgnored() {
        job.onRequested(new AudioResolveRequested(new ObjectId().toHexString()));

        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void clipsOfADisabledSourceDoNotCountAsAudio() {
        AudioPolicy deezerOff = AudioPolicy.of(false, true, true);
        AudioClip oldDeezer = new AudioClip("dz_old", AudioSource.DEEZER, "9", null, null, "Old", 30, "a", null);
        Artist a = save(null, List.of(oldDeezer));
        AudioResolveJob offJob = new AudioResolveJob(mongo, runLog, new AudioResolver(List.of(deezer), deezerOff),
                deezerOff, events, clock, Runnable::run);

        offJob.runAll();

        assertThat(providerCalls).hasValue(0);
        assertThat(artists.findById(a.getId()).orElseThrow().getAudio()).containsExactly(oldDeezer);
        assertThat(recorded.artistEvents()).isEmpty();
    }
}
