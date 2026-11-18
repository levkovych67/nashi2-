package com.nashi.artist;

import com.nashi.telegram.Html;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The admin's {@code /hide} and {@code /show}. Each returns the HTML reply for the admin chat.
 * <p>
 * Hiding keeps the document and only sets {@link ArtistStatus#HIDDEN}, so the Wikipedia import still counts the
 * artist as known. The status changes with a conditional update, so two admins cannot both "win"; an
 * {@link ArtistChangedEvent} is published only when the status really changed.
 */
@Service
public class ArtistVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(ArtistVisibilityService.class);
    private static final Pattern QID = Pattern.compile("[Qq]\\d+");

    private final ArtistRepository artists;
    private final MongoTemplate mongo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ArtistVisibilityService(ArtistRepository artists, MongoTemplate mongo, ApplicationEventPublisher events,
                                   Clock clock) {
        this.artists = artists;
        this.mongo = mongo;
        this.events = events;
        this.clock = clock;
    }

    public String hide(String argument) {
        Optional<Artist> found = find(argument);
        if (found.isEmpty()) {
            return notFound(argument);
        }
        Artist artist = found.get();
        if (artist.getStatus() == ArtistStatus.PUBLISHED && changeStatus(artist, ArtistStatus.PUBLISHED, ArtistStatus.HIDDEN)) {
            log.info("Hid artist {} ({})", artist.getId(), artist.getSlug());
        }
        return "Приховано: " + label(artist) + ". Повторно не імпортується.";
    }

    public String show(String argument) {
        Optional<Artist> found = find(argument);
        if (found.isEmpty()) {
            return notFound(argument);
        }
        Artist artist = found.get();
        if (artist.getStatus() != ArtistStatus.HIDDEN
                || !changeStatus(artist, ArtistStatus.HIDDEN, ArtistStatus.PUBLISHED)) {
            return "Не приховано: " + label(artist);
        }
        log.info("Showed artist {} ({}) again", artist.getId(), artist.getSlug());
        return "Знову показано: " + Html.escape(artist.getName());
    }

    /** Sets {@code to} only while the status is still {@code from}; publishes the event when it was set. */
    private boolean changeStatus(Artist artist, ArtistStatus from, ArtistStatus to) {
        Query query = Query.query(Criteria.where("_id").is(artist.getId()).and("status").is(from));
        Update update = new Update().set("status", to).set("updatedAt", clock.instant()).inc("version", 1);
        Artist before = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false), Artist.class);
        if (before == null) {
            return false;
        }
        events.publishEvent(new ArtistChangedEvent(artist.getId()));
        return true;
    }

    /** A Q-id is looked up by Wikidata id first, then as a slug; anything else as a slug. */
    private Optional<Artist> find(String argument) {
        String key = argument == null ? "" : argument.trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        if (QID.matcher(key).matches()) {
            Optional<Artist> byQid = artists.findByWikidataId(key.toUpperCase(Locale.ROOT));
            if (byQid.isPresent()) {
                return byQid;
            }
        }
        return artists.findBySlug(key);
    }

    private static String label(Artist artist) {
        return Html.escape(artist.getName()) + " (" + Html.escape(artist.getSlug()) + ")";
    }

    private static String notFound(String argument) {
        return "Не знайдено: " + Html.escape(argument == null ? "" : argument.trim());
    }
}
