package com.nashi.wiki;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Names;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class MongoMusicianStoreIT extends MongoIT {

    @Autowired
    private MongoMusicianStore store;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private RecordedEvents recorded;

    @BeforeEach
    void clean() {
        artists.deleteAll();
        recorded.clear();
    }

    private Artist artist(String name, ArtistSource source, ArtistStatus status, String qid, String title) {
        return artists.save(Artist.builder().id(new ObjectId().toHexString()).name(name)
                .slug("s-" + new ObjectId().toHexString()).source(source).status(status).wikidataId(qid)
                .wikiTitle(title).build());
    }

    @Test
    void knownArtistsIncludeHidden() {
        artist("Прихований", ArtistSource.WIKIPEDIA, ArtistStatus.HIDDEN, "Q111", "Прихований (гурт)");
        artist("Гурт з форми", ArtistSource.SUBMISSION, ArtistStatus.HIDDEN, null, null);

        KnownArtists known = store.loadKnown();

        assertThat(known.wikidataIds()).containsExactly("Q111");
        assertThat(known.wikiTitles()).containsExactly("Прихований (гурт)");
        assertThat(known.manualNames()).containsExactly(Names.normalize("Гурт з форми"));
    }

    @Test
    void manualNamesAreThoseOfFormAndV1Artists() {
        artist("«Стара Гвардія»", ArtistSource.V1_MIGRATION, ArtistStatus.PUBLISHED, null, null);
        artist("Збагачений Гурт", ArtistSource.SUBMISSION, ArtistStatus.PUBLISHED, "Q222", "Збагачений Гурт");
        artist("Вікі Гурт", ArtistSource.WIKIPEDIA, ArtistStatus.PUBLISHED, "Q333", "Вікі Гурт (гурт)");

        KnownArtists known = store.loadKnown();

        assertThat(known.manualNames()).containsExactlyInAnyOrder("стара гвардія", "збагачений гурт");
        assertThat(known.wikidataIds()).containsExactlyInAnyOrder("Q222", "Q333");
        assertThat(known.wikiTitles()).containsExactlyInAnyOrder("Збагачений Гурт", "Вікі Гурт (гурт)");
    }

    @Test
    void loadKnownReadsOnlyTheFieldsItNeeds() {
        artist("Гурт", ArtistSource.SUBMISSION, ArtistStatus.PUBLISHED, null, null);
        MongoTemplate spied = spy(mongo);

        new MongoMusicianStore(spied, artists, events).loadKnown();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(spied).find(query.capture(), eq(Document.class), eq("artists"));
        assertThat(query.getValue().getQueryObject()).as("every status counts").isEmpty();
        assertThat(query.getValue().getFieldsObject().keySet())
                .containsExactlyInAnyOrder("wikidataId", "wikiTitle", "nameNormalized", "source");
    }

    @Test
    void saveInsertsAndPublishesArtistChanged() {
        String id = new ObjectId().toHexString();

        Artist saved = store.save(Artist.builder().id(id).slug("okean-elzy").name("Океан Ельзи")
                .source(ArtistSource.WIKIPEDIA).status(ArtistStatus.PUBLISHED).wikidataId("Q1008489").build());

        assertThat(saved.getVersion()).isZero();
        assertThat(artists.findById(id)).get().satisfies(a -> {
            assertThat(a.getNameNormalized()).isEqualTo("океан ельзи");
            assertThat(a.getCreatedAt()).isNotNull();
        });
        assertThat(recorded.artistEvents()).containsExactly(new ArtistChangedEvent(id));
    }

    @Test
    void saveNeverOverwritesAnArtist() {
        Artist existing = artist("Гурт", ArtistSource.SUBMISSION, ArtistStatus.PUBLISHED, null, null);
        recorded.clear();

        assertThatThrownBy(() -> store.save(Artist.builder().id(existing.getId()).slug("other").name("Інший")
                .source(ArtistSource.WIKIPEDIA).status(ArtistStatus.PUBLISHED).build()))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(artists.findById(existing.getId())).get().extracting(Artist::getName).isEqualTo("Гурт");
        assertThat(recorded.artistEvents()).isEmpty();
    }

    @Test
    void slugsAndWikipediaCount() {
        Artist wiki = artist("Вікі", ArtistSource.WIKIPEDIA, ArtistStatus.HIDDEN, "Q1", "Вікі");
        artist("Форма", ArtistSource.SUBMISSION, ArtistStatus.PUBLISHED, null, null);

        assertThat(store.slugExists(wiki.getSlug())).isTrue();
        assertThat(store.slugExists("nobody")).isFalse();
        assertThat(store.countWikipediaArtists()).isEqualTo(1);
    }
}
