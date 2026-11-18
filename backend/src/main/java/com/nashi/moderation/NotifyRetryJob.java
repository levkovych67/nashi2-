package com.nashi.moderation;

import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Every 10 minutes (only when {@code nashi.scheduling.enabled}), sends again the submissions whose notification
 * failed: {@code PENDING}, no control message. Fewer than {@value #FAST_RETRY_FAILURES} {@code FAILED} entries: every
 * run. From then on a submission is never abandoned but retried at most once per {@link #SLOW_RETRY_INTERVAL}, counted
 * from its last {@code FAILED} entry, so a long Telegram outage does not resend the whole backlog every 10 minutes.
 * They are sent one by one on the scheduler thread. A submission younger than {@link #GRACE} is left alone: its first
 * send may still be queued on {@code telegramExecutor}, and sending it here too would post the card twice.
 */
@Component
public class NotifyRetryJob {

    static final int FAST_RETRY_FAILURES = 5;
    static final Duration SLOW_RETRY_INTERVAL = Duration.ofHours(1);
    static final Duration GRACE = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(NotifyRetryJob.class);

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
        Instant now = clock.instant();
        Query query = Query.query(Criteria.where("status").is(SubmissionStatus.PENDING)
                        .and("telegram.controlMessageId").is(null)
                        .and("createdAt").lte(now.minus(GRACE)))
                .with(Sort.by("createdAt"));
        return mongo.find(query, Submission.class).stream()
                .filter(s -> isDue(s, now))
                .map(Submission::getId)
                .toList();
    }

    private static boolean isDue(Submission s, Instant now) {
        List<Instant> failures = s.getModerationLog() == null ? List.of() : s.getModerationLog().stream()
                .filter(e -> e.action() == ModerationAction.FAILED)
                .map(ModerationEntry::at)
                .toList();
        if (failures.size() < FAST_RETRY_FAILURES) {
            return true;
        }
        Instant last = failures.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.MIN);
        return !last.isAfter(now.minus(SLOW_RETRY_INTERVAL));
    }
}
