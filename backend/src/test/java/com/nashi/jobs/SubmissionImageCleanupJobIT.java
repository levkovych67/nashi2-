package com.nashi.jobs;

import com.nashi.media.FileStorage;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import com.nashi.support.MongoIT;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionImageCleanupJobIT extends MongoIT {

    @Autowired
    private SubmissionRepository submissions;
    @Autowired
    private FileStorage storage;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private Clock clock;
    @Autowired
    private SubmissionImageCleanupJob job;

    private final List<String> createdKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        submissions.deleteAll();
        mongo.remove(new Query(), JobRun.class);
    }

    @AfterEach
    void tearDown() {
        createdKeys.forEach(storage::delete);
        submissions.deleteAll();
        mongo.remove(new Query(), JobRun.class);
    }

    private Submission insert(SubmissionStatus status, Duration decidedAgo) {
        String id = new ObjectId().toHexString();
        List<String> keys = List.of("submissions/" + id + "/0.jpg", "submissions/" + id + "/0-thumb.jpg");
        keys.forEach(k -> put(k));
        Submission s = Submission.builder().id(id).status(status).name("Test").imageKeys(keys)
                .createdAt(clock.instant().minus(Duration.ofDays(60)))
                .decidedAt(decidedAgo == null ? null : clock.instant().minus(decidedAgo)).build();
        return submissions.save(s);
    }

    private void put(String key) {
        storage.put(key, new byte[]{1, 2, 3}, "image/jpeg");
        createdKeys.add(key);
    }

    private boolean exists(String key) {
        try {
            storage.get(key);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Test
    void deletesImagesOfRejectedOlderThan30Days() {
        Submission s = insert(SubmissionStatus.REJECTED, Duration.ofDays(31));
        assertThat(job.run()).isEqualTo(1);
        s.getImageKeys().forEach(k -> assertThat(exists(k)).isFalse());
        Submission after = submissions.findById(s.getId()).orElseThrow();
        assertThat(after.getImageKeys()).isEmpty();
        assertThat(after.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
    }

    @Test
    void deletesApprovedOlderThan30DaysButKeepsArtistCopies() {
        Submission s = insert(SubmissionStatus.APPROVED, Duration.ofDays(31));
        String artistKey = "artists/" + new ObjectId().toHexString() + "/0.jpg";
        put(artistKey);
        job.run();
        s.getImageKeys().forEach(k -> assertThat(exists(k)).isFalse());
        assertThat(exists(artistKey)).isTrue();
        assertThat(submissions.findById(s.getId()).orElseThrow().getImageKeys()).isEmpty();
    }

    @Test
    void keepsRecentRejectedAndRecentApprovedAndPending() {
        Submission rejected = insert(SubmissionStatus.REJECTED, Duration.ofDays(29));
        Submission approved = insert(SubmissionStatus.APPROVED, Duration.ofDays(29));
        Submission pending = insert(SubmissionStatus.PENDING, null);
        assertThat(job.run()).isZero();
        for (Submission s : List.of(rejected, approved, pending)) {
            s.getImageKeys().forEach(k -> assertThat(exists(k)).isTrue());
            assertThat(submissions.findById(s.getId()).orElseThrow().getImageKeys()).hasSize(2);
        }
    }

    @Test
    void logsRunWithCount() {
        insert(SubmissionStatus.REJECTED, Duration.ofDays(31));
        job.run();
        JobRun run = mongo.findOne(new Query(), JobRun.class);
        assertThat(run.getJob()).isEqualTo(JobKind.SUBMISSION_CLEANUP);
        assertThat(run.getStatus()).isEqualTo("OK");
        assertThat(run.getReport()).containsEntry("cleaned", 1).containsEntry("filesDeleted", 2);
    }

    @Test
    void failedKeyDoesNotAbortAndKeepsKeysOfThatSubmission() {
        // a storage that fails on one key
        FileStorage flaky = new FileStorage() {
            public void put(String k, byte[] b, String c) { storage.put(k, b, c); }
            public byte[] get(String k) { return storage.get(k); }
            public void copy(String a, String b) { storage.copy(a, b); }
            public void delete(String k) {
                if (k.endsWith("-thumb.jpg") && k.equals(badKey)) {
                    throw new IllegalStateException("boom");
                }
                storage.delete(k);
            }
            public String publicUrl(String k) { return storage.publicUrl(k); }
        };
        Submission bad = insert(SubmissionStatus.REJECTED, Duration.ofDays(31));
        Submission good = insert(SubmissionStatus.REJECTED, Duration.ofDays(31));
        badKey = bad.getImageKeys().get(1);
        SubmissionImageCleanupJob flakyJob = new SubmissionImageCleanupJob(mongo, flaky, clock,
                new JobRunLog(mongo, clock, org.mockito.Mockito.mock(com.nashi.telegram.TelegramGateway.class)));

        assertThat(flakyJob.run()).isEqualTo(1);
        assertThat(submissions.findById(good.getId()).orElseThrow().getImageKeys()).isEmpty();
        assertThat(submissions.findById(bad.getId()).orElseThrow().getImageKeys()).hasSize(2);
        JobRun run = mongo.findOne(new Query(), JobRun.class);
        assertThat(run.getReport()).containsEntry("filesFailed", 1);
    }

    private String badKey;
}
