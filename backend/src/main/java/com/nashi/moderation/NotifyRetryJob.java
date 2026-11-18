package com.nashi.moderation;

import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoExpression;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Every 10 minutes (only when {@code nashi.scheduling.enabled}), sends again the submissions whose notification
 * failed: {@code PENDING}, no control message, fewer than {@value #MAX_FAILURES} {@code FAILED} entries. They are
 * sent one by one on the scheduler thread. A submission younger than {@link #GRACE} is left alone: its first send may
 * still be queued on {@code telegramExecutor}, and sending it here too would post the card twice.
 */
@Component
public class NotifyRetryJob {

    static final int MAX_FAILURES = 5;
    static final Duration GRACE = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(NotifyRetryJob.class);
    private static final String FEWER_FAILURES_THAN_MAX = "{ $lt: [ { $size: { $filter: {"
            + " input: { $ifNull: ['$moderationLog', []] }, cond: { $eq: ['$$this.action', 'FAILED'] } } } }, "
            + MAX_FAILURES + " ] }";

    private final MongoTemplate mongo;
    private final SubmissionNotifier notifier;
    private final Clock clock;

    public NotifyRetryJob(MongoTemplate mongo, SubmissionNotifier notifier, Clock clock) {
        this.mongo = mongo;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void run() {
        List<String> due = dueSubmissionIds();
        if (!due.isEmpty()) {
            log.info("Sending {} submission(s) to moderation again", due.size());
        }
        due.forEach(notifier::send);
    }

    /** Ids of the submissions to send again, oldest first. */
    List<String> dueSubmissionIds() {
        Query query = Query.query(Criteria.where("status").is(SubmissionStatus.PENDING)
                        .and("telegram.controlMessageId").is(null)
                        .and("createdAt").lte(clock.instant().minus(GRACE)))
                .addCriteria(Criteria.expr(MongoExpression.create(FEWER_FAILURES_THAN_MAX)))
                .with(Sort.by("createdAt"));
        return mongo.find(query, Submission.class).stream().map(Submission::getId).toList();
    }
}
