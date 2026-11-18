package com.nashi.admin;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistStatus;
import com.nashi.submission.Submission;
import com.nashi.submission.SubmissionRepository;
import com.nashi.submission.SubmissionStatus;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * The numbers for {@code /stats}. "Не надіслано" counts {@code PENDING} submissions that have no control message in the
 * group more than {@link #UNSENT_AFTER} after they came in (the notification failed and {@code NotifyRetryJob} keeps
 * trying), so moderators learn about submissions they cannot see.
 */
@Service
public class StatsService {

    static final Duration UNSENT_AFTER = Duration.ofMinutes(5);

    private final MongoTemplate mongo;
    private final SubmissionRepository submissions;
    private final Clock clock;

    public StatsService(MongoTemplate mongo, SubmissionRepository submissions, Clock clock) {
        this.mongo = mongo;
        this.submissions = submissions;
        this.clock = clock;
    }

    public String summary() {
        Criteria published = Criteria.where("status").is(ArtistStatus.PUBLISHED);
        long onMap = count(new Criteria().andOperator(published, Criteria.where("pin").ne(null)));
        long noPin = count(new Criteria().andOperator(published, Criteria.where("pin").is(null)));
        long withAudio = count(new Criteria().andOperator(published, Criteria.where("audio.0").exists(true)));
        long pending = submissions.countByStatus(SubmissionStatus.PENDING);
        return "Музикантів на карті: " + onMap + " · без піна: " + noPin + " · з аудіо: " + withAudio
                + " · заявок на модерації: " + pending + " · лайків за весь час: " + totalLikes()
                + " · не надіслано: " + unsent();
    }

    private long unsent() {
        return mongo.count(Query.query(Criteria.where("status").is(SubmissionStatus.PENDING)
                .and("telegram.controlMessageId").is(null)
                .and("createdAt").lte(clock.instant().minus(UNSENT_AFTER))), Submission.class);
    }

    private long count(Criteria criteria) {
        return mongo.count(Query.query(criteria), Artist.class);
    }

    private long totalLikes() {
        Document result = mongo.aggregate(
                Aggregation.newAggregation(Aggregation.group().sum("likesCount").as("total")),
                Artist.class, Document.class).getUniqueMappedResult();
        return result == null || result.get("total") == null ? 0 : ((Number) result.get("total")).longValue();
    }
}
