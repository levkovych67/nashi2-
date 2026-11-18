package com.nashi.moderation;

import com.mongodb.client.result.UpdateResult;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.SlugAllocator;
import com.nashi.submission.ModerationAction;
import com.nashi.submission.ModerationEntry;
import com.nashi.submission.Moderator;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionStatus;
import com.nashi.telegram.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Applies a moderator's decision. The first click wins: one {@code findAndModify} moves the submission out of
 * {@code PENDING} and logs the decision, so a concurrent or late click changes nothing and gets
 * {@link AlreadyDecidedException}. For {@code YES} the artist is then created; if that fails, the submission goes
 * back to {@code PENDING} with a {@code FAILED} entry (copied photos are removed) so the moderators can press again.
 * Events are published only after the artist is saved.
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final MongoTemplate mongo;
    private final ArtistRepository artists;
    private final ArtistFromSubmission artistFactory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ModerationService(MongoTemplate mongo, ArtistRepository artists, ArtistFromSubmission artistFactory,
                             ApplicationEventPublisher events, Clock clock) {
        this.mongo = mongo;
        this.artists = artists;
        this.artistFactory = artistFactory;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @throws AlreadyDecidedException the submission is not {@code PENDING} (nothing was changed)
     * @throws RuntimeException        creating the artist failed; the submission is {@code PENDING} again
     */
    public DecisionResult decide(String submissionId, Verdict verdict, Moderator by) {
        Instant now = clock.instant();
        boolean approve = verdict == Verdict.YES;
        Update decision = new Update()
                .set("status", approve ? SubmissionStatus.APPROVED : SubmissionStatus.REJECTED)
                .set("decidedAt", now)
                .push("moderationLog", new ModerationEntry(
                        approve ? ModerationAction.APPROVED : ModerationAction.REJECTED, by, now, null));
        Submission s = mongo.findAndModify(byIdAndStatus(submissionId, SubmissionStatus.PENDING), decision,
                FindAndModifyOptions.options().returnNew(true), Submission.class);
        if (s == null) {
            throw new AlreadyDecidedException(mongo.findById(submissionId, Submission.class));
        }
        if (!approve) {
            return new DecisionResult(verdict, s, null);
        }
        Artist artist = createArtist(s, by);
        publish(new ArtistChangedEvent(artist.getId()));
        if (s.getWikipediaUrl() != null && !s.getWikipediaUrl().isBlank()) {
            publish(new EnrichmentRequested(artist.getId(), s.getWikipediaUrl()));
        }
        publish(new AudioResolveRequested(artist.getId()));
        return new DecisionResult(verdict, s, artist);
    }

    /**
     * Builds the artist, records its id on the submission and saves the artist, in this order so nothing can fail
     * after the artist exists. Any failure reverts the submission and is rethrown.
     */
    private Artist createArtist(Submission s, Moderator by) {
        Artist artist = null;
        try {
            artist = artistFactory.create(s);
            mongo.updateFirst(byIdAndStatus(s.getId(), SubmissionStatus.APPROVED),
                    Update.update("artistId", artist.getId()), Submission.class);
            Artist saved = insert(artist);
            s.setArtistId(saved.getId());
            return saved;
        } catch (RuntimeException e) {
            if (artist != null) {
                artistFactory.discard(artist);
            }
            revert(s.getId(), by, e);
            throw e;
        }
    }

    /** Saves a new artist; when its slug was taken since it was allocated, allocates once more. */
    private Artist insert(Artist artist) {
        try {
            return artists.save(artist);
        } catch (DuplicateKeyException e) {
            if (!isSlugConflict(e)) {
                throw e;
            }
            String taken = artist.getSlug();
            // The failed insert already set the version; null makes the next save an insert again.
            artist.setVersion(null);
            artist.setSlug(SlugAllocator.allocate(artist.getName(), artists::existsBySlug));
            log.info("Slug {} was taken meanwhile; artist {} gets {}", taken, artist.getId(), artist.getSlug());
            return artists.save(artist);
        }
    }

    private static boolean isSlugConflict(DuplicateKeyException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.contains("slug_unique");
    }

    /** Back to {@code PENDING}, only if this approval still holds; a failure here is logged and kept as suppressed. */
    private void revert(String submissionId, Moderator by, RuntimeException cause) {
        String reason = FailureReason.of(cause);
        try {
            UpdateResult result = mongo.updateFirst(byIdAndStatus(submissionId, SubmissionStatus.APPROVED),
                    new Update().set("status", SubmissionStatus.PENDING).unset("decidedAt").unset("artistId")
                            .push("moderationLog",
                                    new ModerationEntry(ModerationAction.FAILED, by, clock.instant(), reason)),
                    Submission.class);
            if (result.getMatchedCount() == 0) {
                log.warn("Submission {} was no longer APPROVED, not reverted after: {}", submissionId, reason);
            } else {
                log.warn("Approving submission {} failed, it is PENDING again: {}", submissionId, reason);
            }
        } catch (RuntimeException e) {
            cause.addSuppressed(e);
            log.error("Could not put submission {} back to PENDING", submissionId, e);
        }
    }

    /** The decision has taken effect; a failing listener must not turn it into an error for the moderator. */
    private void publish(Object event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException e) {
            log.error("A listener of {} failed", event, e);
        }
    }

    private static Query byIdAndStatus(String submissionId, SubmissionStatus status) {
        return Query.query(Criteria.where("_id").is(submissionId).and("status").is(status));
    }
}
