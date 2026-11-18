package com.nashi.moderation;

import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import com.nashi.submission.TelegramRefs;
import com.nashi.support.MongoIT;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** The selection runs against MongoDB; the notifier is a mock so nothing is sent. */
class NotifyRetryJobIT extends MongoIT {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Autowired
    private SubmissionRepository submissions;
    @Autowired
    private MongoTemplate mongo;

    private final SubmissionNotifier notifier = mock(SubmissionNotifier.class);
    private NotifyRetryJob job;

    @BeforeEach
    void setUp() {
        submissions.deleteAll();
        job = new NotifyRetryJob(mongo, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        submissions.deleteAll();
    }

    private String insert(SubmissionStatus status, TelegramRefs telegram, int failures, Duration age) {
        List<ModerationEntry> log = new ArrayList<>();
        log.add(new ModerationEntry(ModerationAction.DUPLICATE_WARNING, null, NOW, "a1"));
        for (int i = 0; i < failures; i++) {
            log.add(new ModerationEntry(ModerationAction.FAILED, null, NOW, "TelegramGatewayException: x"));
        }
        String id = new ObjectId().toHexString();
        submissions.save(Submission.builder().id(id).status(status).name("Гурт").telegram(telegram)
                .moderationLog(log).build());
        // createdAt is set by auditing from the real clock; move it to where the test needs it
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("createdAt", NOW.minus(age)),
                Submission.class);
        return id;
    }

    @Test
    void selectsPendingUnsentSubmissionsWithFewerThanFiveFailuresOldestFirst() {
        String newer = insert(SubmissionStatus.PENDING, new TelegramRefs(-1L, null, List.of(501), null), 4,
                Duration.ofMinutes(20));
        String older = insert(SubmissionStatus.PENDING, null, 0, Duration.ofHours(2));
        // gave up after five failures
        insert(SubmissionStatus.PENDING, null, 5, Duration.ofHours(1));
        // sent
        insert(SubmissionStatus.PENDING, new TelegramRefs(-1L, null, List.of(501), 503), 0, Duration.ofHours(1));
        // decided, or the bot trap
        insert(SubmissionStatus.APPROVED, null, 0, Duration.ofHours(1));
        insert(SubmissionStatus.DISCARDED, null, 0, Duration.ofHours(1));
        // the first send may still be under way
        insert(SubmissionStatus.PENDING, null, 0, Duration.ofMinutes(2));

        assertThat(job.dueSubmissionIds()).containsExactly(older, newer);
    }

    @Test
    void runNotifiesEachDueSubmissionInTurn() {
        String older = insert(SubmissionStatus.PENDING, null, 1, Duration.ofHours(2));
        String newer = insert(SubmissionStatus.PENDING, null, 0, Duration.ofHours(1));

        job.run();

        InOrder order = inOrder(notifier);
        order.verify(notifier).send(older);
        order.verify(notifier).send(newer);
        verifyNoMoreInteractions(notifier);
    }
}
