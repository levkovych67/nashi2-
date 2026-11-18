package com.nashi.jobs;

import com.nashi.media.FileStorage;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightly at 04:30 Kyiv time: removes the {@code submissions/...} photos of {@code REJECTED} and {@code APPROVED}
 * submissions decided more than {@value #RETENTION_DAYS} days ago. Approved photos live on under {@code artists/...}
 * (copied at approval) and are never touched. Deletion is best effort per key; {@code imageKeys} is emptied with a
 * targeted {@code $set}, only for submissions whose keys were all deleted, so a failed one is retried next night.
 */
@Component
public class SubmissionImageCleanupJob {

    static final int RETENTION_DAYS = 30;
    private static final String PRIVATE_PREFIX = "submissions/";
    private static final Logger log = LoggerFactory.getLogger(SubmissionImageCleanupJob.class);

    private final MongoTemplate mongo;
    private final FileStorage storage;
    private final Clock clock;
    private final JobRunLog runLog;

    public SubmissionImageCleanupJob(MongoTemplate mongo, FileStorage storage, Clock clock, JobRunLog runLog) {
        this.mongo = mongo;
        this.storage = storage;
        this.clock = clock;
        this.runLog = runLog;
    }

    /** @return the number of submissions whose photos were removed */
    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Kyiv")
    public int run() {
        Map<String, Object> report = runLog.run(JobKind.SUBMISSION_CLEANUP, this::cleanUp);
        return (Integer) report.get("cleaned");
    }

    private Map<String, Object> cleanUp() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(RETENTION_DAYS));
        List<Submission> due = mongo.find(Query.query(new Criteria().andOperator(
                Criteria.where("status").in(SubmissionStatus.REJECTED, SubmissionStatus.APPROVED),
                Criteria.where("decidedAt").lt(cutoff),
                Criteria.where("imageKeys.0").exists(true))), Submission.class);

        int cleaned = 0;
        int filesDeleted = 0;
        int filesFailed = 0;
        for (Submission s : due) {
            boolean allDeleted = true;
            for (String key : s.getImageKeys()) {
                if (!key.startsWith(PRIVATE_PREFIX)) {
                    continue;
                }
                try {
                    storage.delete(key);
                    filesDeleted++;
                } catch (RuntimeException e) {
                    allDeleted = false;
                    filesFailed++;
                    log.warn("Could not delete {} of submission {}", key, s.getId(), e);
                }
            }
            if (allDeleted) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(s.getId())),
                        new Update().set("imageKeys", List.of()), Submission.class);
                cleaned++;
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cleaned", cleaned);
        report.put("filesDeleted", filesDeleted);
        report.put("filesFailed", filesFailed);
        return report;
    }
}
