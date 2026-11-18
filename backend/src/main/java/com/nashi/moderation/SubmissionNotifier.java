package com.nashi.moderation;

import com.nashi.media.FileStorage;
import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionCreatedEvent;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import com.nashi.submission.TelegramRefs;
import com.nashi.telegram.NamedBytes;
import com.nashi.telegram.TelegramGateway;
import com.nashi.telegram.TelegramProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Posts a new submission to the moderation chat: the photos as an album (uploaded as bytes, because
 * {@code submissions/} is private), then the card with the buttons as a reply to the first photo. The message ids
 * are stored with a {@code SENT} entry (and {@code DUPLICATE_WARNING} for a possible duplicate). A failure is logged
 * as {@code FAILED} and keeps the album ids, so {@link NotifyRetryJob} sends only what is missing.
 */
@Component
public class SubmissionNotifier {

    private static final Logger log = LoggerFactory.getLogger(SubmissionNotifier.class);

    private final SubmissionRepository submissions;
    private final FileStorage storage;
    private final TelegramGateway gateway;
    private final TelegramProperties telegram;
    private final SubmissionCardFormatter cards;
    private final Clock clock;

    public SubmissionNotifier(SubmissionRepository submissions, FileStorage storage, TelegramGateway gateway,
                              TelegramProperties telegram, SubmissionCardFormatter cards, Clock clock) {
        this.submissions = submissions;
        this.storage = storage;
        this.gateway = gateway;
        this.telegram = telegram;
        this.cards = cards;
        this.clock = clock;
    }

    @Async("telegramExecutor")
    @EventListener
    public void onCreated(SubmissionCreatedEvent event) {
        send(event.submissionId());
    }

    /**
     * Sends a {@code PENDING} submission that has no control message yet; anything else is skipped. Never throws.
     * With sending disabled nothing is read or stored, so the submission stays due for {@link NotifyRetryJob}.
     */
    public void send(String submissionId) {
        Submission s = submissions.findById(submissionId).orElse(null);
        if (s == null || s.getStatus() != SubmissionStatus.PENDING || controlMessageId(s.getTelegram()) != null) {
            log.debug("Submission {} needs no notification", submissionId);
            return;
        }
        if (!telegram.sendingEnabled()) {
            log.info("Telegram sending off, submission {} not sent to moderation", submissionId);
            return;
        }
        List<Integer> album = albumMessageIds(s.getTelegram());
        try {
            if (album.isEmpty()) {
                album = gateway.sendAlbum(photos(s));
            }
            int controlId = gateway.sendControl(cards.card(s), SubmissionCardFormatter.buttons(submissionId),
                    album.isEmpty() ? null : album.getFirst());
            if (controlId < 0) {
                log.info("Telegram sending off, submission {} not sent to moderation", submissionId);
                return;
            }
            Instant now = clock.instant();
            List<ModerationEntry> entries = entries(s);
            entries.add(new ModerationEntry(ModerationAction.SENT, null, now, null));
            if (s.getDuplicateOfArtistId() != null) {
                entries.add(new ModerationEntry(ModerationAction.DUPLICATE_WARNING, null, now,
                        s.getDuplicateOfArtistId()));
            }
            s.setTelegram(refs(album, controlId));
            s.setModerationLog(entries);
            submissions.save(s);
        } catch (RuntimeException e) {
            recordFailure(s, album, e);
        }
    }

    private void recordFailure(Submission s, List<Integer> album, RuntimeException cause) {
        String reason = FailureReason.of(cause);
        log.warn("Sending submission {} to moderation failed: {}", s.getId(), reason);
        try {
            List<ModerationEntry> entries = entries(s);
            entries.add(new ModerationEntry(ModerationAction.FAILED, null, clock.instant(), reason));
            if (!album.isEmpty()) {
                s.setTelegram(refs(album, null));
            }
            s.setModerationLog(entries);
            submissions.save(s);
        } catch (RuntimeException e) {
            log.error("Could not record the failed notification of submission {}", s.getId(), e);
        }
    }

    private List<NamedBytes> photos(Submission s) {
        List<NamedBytes> photos = new ArrayList<>();
        for (String key : ArtistFromSubmission.fullImageKeys(s)) {
            photos.add(new NamedBytes(key.substring(key.lastIndexOf('/') + 1), storage.get(key)));
        }
        return photos;
    }

    private TelegramRefs refs(List<Integer> album, @Nullable Integer controlMessageId) {
        return new TelegramRefs(telegram.moderation().chatId(), telegram.moderation().threadId(), List.copyOf(album),
                controlMessageId);
    }

    private static List<ModerationEntry> entries(Submission s) {
        return s.getModerationLog() == null ? new ArrayList<>() : new ArrayList<>(s.getModerationLog());
    }

    private static @Nullable Integer controlMessageId(@Nullable TelegramRefs refs) {
        return refs == null ? null : refs.controlMessageId();
    }

    private static List<Integer> albumMessageIds(@Nullable TelegramRefs refs) {
        return refs == null || refs.albumMessageIds() == null ? List.of() : refs.albumMessageIds();
    }
}
