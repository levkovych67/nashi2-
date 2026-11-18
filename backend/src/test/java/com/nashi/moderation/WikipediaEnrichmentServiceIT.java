package com.nashi.moderation;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.LifeDates;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.artist.TextSource;
import com.nashi.artist.Work;
import com.nashi.artist.WorkType;
import com.nashi.config.NashiProperties;
import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import com.nashi.support.RecordedEvents;
import com.nashi.telegram.TelegramGateway;
import com.nashi.wiki.ArtistCandidateSource;
import com.nashi.wiki.TestArtists;
import com.nashi.wiki.WikiArtistData;
import com.nashi.wiki.WikiCandidate;
import com.nashi.wiki.WikiLink;
import com.nashi.wiki.WikiWork;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The enrichment's targeted update against MongoDB: records round-trip, origin, pin and likes stay. */
class WikipediaEnrichmentServiceIT extends MongoIT {

    private static final String QID = "Q990201";
    private static final Origin LVIV = new Origin("Q36036", "Львів", Region.LVIV, OriginBasis.FORMATION,
            PinPrecision.CITY, new GeoJsonPoint(24.03, 49.84));

    @Autowired
    private ArtistRepository artists;
    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private RecordedEvents recorded;
    @Autowired
    private NashiProperties properties;
    @Autowired
    private Clock clock;

    @BeforeEach
    void clean() {
        artists.deleteAll();
        recorded.clear();
    }

    @Test
    void storesTheEnrichmentWithoutTouchingOriginPinOrLikes() {
        String id = new ObjectId().toHexString();
        ImageAsset userPhoto = new ImageAsset("artists/" + id + "/0.jpg", null, "artists/" + id + "/0-thumb.jpg",
                null, "Гурт", null);
        GeoJsonPoint pin = new GeoJsonPoint(24.01, 49.83);
        Artist submitted = artists.save(Artist.builder().id(id).slug("hurt-" + id).name("Гурт").kind(MusicianKind.BAND)
                .status(ArtistStatus.PUBLISHED).source(ArtistSource.SUBMISSION)
                .summary("Опис від заявника, достатньо довгий для форми.").origin(LVIV).pin(pin)
                .images(List.of(userPhoto)).links(List.of(new Link(LinkPlatform.BANDCAMP, "https://hurt.bandcamp.com")))
                .build());
        WikiArtistData found = TestArtists.band(QID, "Гурт").wikiTitle("Гурт (гурт)").tagline("український гурт")
                .biography("«Гурт» (засн. 2001) — український гурт.").years(2001, 2020).genres(List.of("рок"))
                .works(List.of(new WikiWork("Q990202", "Перший", 2003, null, null, null, null)))
                .photo("Hurt.jpg", "Автор", "CC BY-SA 4.0")
                .link(WikiLink.Platform.WEBSITE, "https://hurt.example")
                .build();
        ArtistCandidateSource source = new ArtistCandidateSource() {
            @Override
            public List<WikiCandidate> listCandidates() {
                return List.of();
            }

            @Override
            public List<WikiArtistData> fetchDetails(List<WikiCandidate> candidates) {
                // a like arrives while Wikidata is being asked
                mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().inc("likesCount", 7),
                        Artist.class);
                return List.of(found);
            }
        };
        String pageprops = "{\"query\":{\"pages\":[{\"title\":\"Гурт (гурт)\",\"pageprops\":{\"wikibase_item\":\""
                + QID + "\"}}]}}";
        WikipediaEnrichmentService service = new WikipediaEnrichmentService(artists, mongo, () -> source,
                url -> pageprops, mock(TelegramGateway.class), events, properties, clock, true);

        assertThat(service.enrich(id, "https://uk.wikipedia.org/wiki/Гурт_(гурт)")).isTrue();

        Artist stored = artists.findById(id).orElseThrow();
        assertThat(stored.getWikidataId()).isEqualTo(QID);
        assertThat(stored.getWikiTitle()).isEqualTo("Гурт (гурт)");
        assertThat(stored.getSummary()).isEqualTo("«Гурт» — український гурт.");
        assertThat(stored.getTextSource()).extracting(TextSource::license).isEqualTo("CC BY-SA 4.0");
        assertThat(stored.getDates()).isEqualTo(new LifeDates(null, null, "2001", "2020"));
        assertThat(stored.getWorks()).containsExactly(new Work("Перший", 2003, WorkType.ALBUM, null));
        assertThat(stored.getLinks()).containsExactly(new Link(LinkPlatform.BANDCAMP, "https://hurt.bandcamp.com"),
                new Link(LinkPlatform.WEBSITE, "https://hurt.example"));
        assertThat(stored.getImages()).hasSize(2).first().isEqualTo(userPhoto);
        assertThat(stored.getImages().get(1).credit().author()).isEqualTo("Автор");
        assertThat(stored.getOrigin()).isEqualTo(LVIV);
        assertThat(stored.getPin()).isEqualTo(pin);
        assertThat(stored.getName()).isEqualTo("Гурт");
        assertThat(stored.getLikesCount()).isEqualTo(7);
        // the like's update bumped the version too (Spring Data adds $inc version), so the first $set missed and the
        // enrichment was applied again to the fresh document
        assertThat(stored.getVersion()).isEqualTo(submitted.getVersion() + 2);
        assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(submitted.getUpdatedAt());
        assertThat(recorded.artistEvents()).containsExactly(new ArtistChangedEvent(id));
    }
}
