package com.nashi.wiki;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import org.bson.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * The import's view of {@code artists}. Every artist counts as known by its Wikidata id and article title, whatever
 * its status (a hidden one must not come back). Artists added through the form or migrated from V1 that have no
 * Wikidata id also count by {@code nameNormalized}, so the import does not add a second copy of them; one with an id
 * (an enriched submission) is matched by the id alone, so a different Wikipedia namesake is still imported (as V1).
 * The ids and titles in {@code wiki_denylist} count as known too.
 */
@Component
public class MongoMusicianStore implements ArtistStore {

    private static final String COLLECTION = "artists";
    /** Wikipedia artists hidden by V1 administrators ({@code V1MigrationJob}); the import must not recreate them. */
    static final String DENYLIST = "wiki_denylist";
    private static final Set<String> MANUAL_SOURCES =
            Set.of(ArtistSource.SUBMISSION.name(), ArtistSource.V1_MIGRATION.name());

    private final MongoTemplate mongo;
    private final ArtistRepository artists;
    private final ApplicationEventPublisher events;

    public MongoMusicianStore(MongoTemplate mongo, ArtistRepository artists, ApplicationEventPublisher events) {
        this.mongo = mongo;
        this.artists = artists;
        this.events = events;
    }

    /** Reads only the four fields it needs of every artist, and the denylist. */
    @Override
    public KnownArtists loadKnown() {
        Query query = new Query();
        query.fields().include("wikidataId", "wikiTitle", "nameNormalized", "source");
        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        Set<String> manualNames = new HashSet<>();
        for (Document artist : mongo.find(query, Document.class, COLLECTION)) {
            String wikidataId = artist.getString("wikidataId");
            addIfPresent(ids, wikidataId);
            addIfPresent(titles, artist.getString("wikiTitle"));
            if (MANUAL_SOURCES.contains(artist.getString("source")) && (wikidataId == null || wikidataId.isBlank())) {
                addIfPresent(manualNames, artist.getString("nameNormalized"));
            }
        }
        for (Document denied : mongo.find(new Query(), Document.class, DENYLIST)) {
            addIfPresent(ids, denied.getString("wikidataId"));
            addIfPresent(titles, denied.getString("wikiTitle"));
        }
        return new KnownArtists(ids, titles, manualNames);
    }

    @Override
    public boolean slugExists(String slug) {
        return artists.existsBySlug(slug);
    }

    @Override
    public long countWikipediaArtists() {
        return mongo.count(Query.query(Criteria.where("source").is(ArtistSource.WIKIPEDIA)), Artist.class);
    }

    /**
     * Inserts the new artist (never overwrites one with the same id) and publishes {@link ArtistChangedEvent}, so the
     * map snapshot is rebuilt.
     */
    @Override
    public Artist save(Artist artist) {
        Artist saved = artists.insert(artist);
        events.publishEvent(new ArtistChangedEvent(saved.getId()));
        return saved;
    }

    private static void addIfPresent(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value);
        }
    }
}
