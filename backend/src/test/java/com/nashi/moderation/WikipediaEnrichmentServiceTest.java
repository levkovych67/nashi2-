package com.nashi.moderation;

import com.mongodb.client.result.UpdateResult;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistChangedEvent;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.ImageCredit;
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
import com.nashi.telegram.TelegramGateway;
import com.nashi.wiki.ArtistCandidateSource;
import com.nashi.wiki.CandidateGroup;
import com.nashi.wiki.LocationBasis;
import com.nashi.wiki.TestArtists;
import com.nashi.wiki.WikiArtistData;
import com.nashi.wiki.WikiCandidate;
import com.nashi.wiki.WikiHttp;
import com.nashi.wiki.WikiLink;
import com.nashi.wiki.WikiPlace;
import com.nashi.wiki.WikiWork;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WikipediaEnrichmentServiceTest {

    private static final String ID = "665f1c2e9b1e4a0001a1b2c3";
    private static final String QID = "Q1008489";
    private static final String URL = "https://uk.wikipedia.org/wiki/Океан_Ельзи";
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Origin LVIV = new Origin("Q36036", "Львів", Region.LVIV, OriginBasis.FORMATION,
            PinPrecision.CITY, new GeoJsonPoint(24.03, 49.84));
    private static final GeoJsonPoint PIN = new GeoJsonPoint(24.01, 49.83);
    private static final ImageAsset USER_PHOTO = new ImageAsset("artists/" + ID + "/0.jpg", null,
            "artists/" + ID + "/0-thumb.jpg", null, "Океан Ельзи", null);
    private static final String SPOTIFY = "https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS";
    private static final String CHANNEL = "https://www.youtube.com/channel/UCjJ-oj2aJTINILYLDv2MFEQ";

    private final ArtistRepository artists = mock(ArtistRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final List<String> requests = new ArrayList<>();
    private final List<WikiCandidate> asked = new ArrayList<>();
    private WikiArtistData found = TestArtists.band(QID, "Океан Ельзи")
            .tagline("український рок-гурт")
            .biography("«Океан Ельзи» (засн. 12 жовтня 1994) — український рок-гурт зі Львова.")
            .years(1994, null)
            .genres(List.of("рок", "поп-рок"))
            .works(List.of(new WikiWork("Q2445442", "Там, де нас нема", 1998, null, null, null,
                    "https://uk.wikipedia.org/wiki/Tam")))
            .place(new WikiPlace("Q36036", "Львів", 49.84, 24.03, Region.LVIV), LocationBasis.FORMATION)
            .photo("OkeanElzi25.jpg", "Alexander Khanin", "CC BY 3.0")
            .link(WikiLink.Platform.YOUTUBE, CHANNEL)
            .link(WikiLink.Platform.SPOTIFY, SPOTIFY + "/")
            .youtubeChannelUrl(CHANNEL)
            .build();
    private String pageprops = "{\"batchcomplete\":true,\"query\":{\"pages\":[{\"pageid\":63871,\"ns\":0,"
            + "\"title\":\"Океан Ельзи\",\"pageprops\":{\"wikibase_item\":\"" + QID + "\"}}]}}";
    private final WikiHttp http = url -> {
        requests.add(URLDecoder.decode(url, StandardCharsets.UTF_8));
        return pageprops;
    };
    private final ArtistCandidateSource source = new ArtistCandidateSource() {
        @Override
        public List<WikiCandidate> listCandidates() {
            throw new AssertionError("enrichment never lists candidates");
        }

        @Override
        public List<WikiArtistData> fetchDetails(List<WikiCandidate> candidates) {
            asked.addAll(candidates);
            return found == null ? List.of() : List.of(found);
        }
    };

    private Artist submitted;

    @BeforeEach
    void setUp() {
        submitted = Artist.builder().id(ID).version(3L).slug("okean-elzy").name("Океан Ельзи")
                .kind(MusicianKind.BAND).status(ArtistStatus.PUBLISHED).source(ArtistSource.SUBMISSION)
                .summary("Гурт зі Львова, який заявник описав своїми словами для перевірки.")
                .origin(LVIV).pin(PIN).images(List.of(USER_PHOTO))
                .links(List.of(new Link(LinkPlatform.SPOTIFY, SPOTIFY))).genres(List.of("рок"))
                .likesCount(5).build();
        when(artists.findById(ID)).thenReturn(Optional.of(submitted));
        when(artists.findByWikidataId(QID)).thenReturn(Optional.empty());
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Artist.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    private WikipediaEnrichmentService service(boolean enabled) {
        return new WikipediaEnrichmentService(artists, mongo, () -> source, http, gateway, events,
                new NashiProperties(null, null, null, null, null, null, "https://nashi.example"),
                Clock.fixed(NOW, ZoneOffset.UTC), enabled);
    }

    private WikipediaEnrichmentService service() {
        return service(true);
    }

    private Document set(Update update) {
        return (Document) update.getUpdateObject().get("$set");
    }

    @Test
    void enrichFillsWikiFieldsKeepsOriginAndUserPhotosFirst() {
        assertThat(service().enrich(ID, URL)).isTrue();

        assertThat(requests).containsExactly("https://uk.wikipedia.org/w/api.php?action=query&format=json"
                + "&formatversion=2&prop=pageprops&ppprop=wikibase_item&redirects=1&titles=Океан Ельзи");
        assertThat(asked).containsExactly(new WikiCandidate(QID, "Океан Ельзи", CandidateGroup.ENSEMBLE));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(Artist.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("_id", ID).append("version", 3L));
        Document set = set(update.getValue());
        assertThat(set.get("wikidataId")).isEqualTo(QID);
        assertThat(set.get("wikiTitle")).isEqualTo("Океан Ельзи");
        assertThat(set.get("tagline")).isEqualTo("український рок-гурт");
        assertThat(set.get("summary")).isEqualTo("«Океан Ельзи» — український рок-гурт зі Львова.");
        assertThat(set.get("description")).isEqualTo("«Океан Ельзи» — український рок-гурт зі Львова.");
        assertThat(set.get("textSource")).isEqualTo(new TextSource("WIKIPEDIA", "Океан Ельзи",
                "https://uk.wikipedia.org/wiki/%D0%9E%D0%BA%D0%B5%D0%B0%D0%BD_%D0%95%D0%BB%D1%8C%D0%B7%D0%B8",
                "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/deed.uk"));
        assertThat(set.get("dates")).isEqualTo(new LifeDates(null, null, "1994", null));
        assertThat(set.get("genres")).isEqualTo(List.of("рок", "поп-рок"));
        assertThat(set.get("works")).isEqualTo(List.of(
                new Work("Там, де нас нема", 1998, WorkType.ALBUM, "https://uk.wikipedia.org/wiki/Tam")));
        assertThat(set.get("links")).as("the submitter's first; the same Spotify page is not added twice")
                .isEqualTo(List.of(new Link(LinkPlatform.SPOTIFY, SPOTIFY), new Link(LinkPlatform.YOUTUBE, CHANNEL)));
        assertThat(set.get("youtubeChannelUrl")).isEqualTo(CHANNEL);
        assertThat(set.get("images")).isEqualTo(List.of(USER_PHOTO, new ImageAsset(null,
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/OkeanElzi25.jpg/800px-OkeanElzi25.jpg",
                null, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/OkeanElzi25.jpg/400px-OkeanElzi25.jpg",
                "Океан Ельзи", new ImageCredit("Alexander Khanin", "CC BY 3.0", null,
                "https://commons.wikimedia.org/wiki/File:OkeanElzi25.jpg"))));
        assertThat(set.get("updatedAt")).isEqualTo(NOW);
        assertThat(set.keySet()).doesNotContain("origin", "pin", "name", "slug", "status", "likesCount", "kind");
        assertThat(update.getValue().getUpdateObject().get("$inc")).isEqualTo(new Document("version", 1));
        verify(events).publishEvent(new ArtistChangedEvent(ID));
        verifyNoInteractions(gateway);
    }

    @Test
    void enrichSkipsWhenQidBelongsToAnotherArtist() {
        when(artists.findByWikidataId(QID)).thenReturn(Optional.of(Artist.builder().id("other").slug("okean-elzy-1994")
                .name("Океан Ельзи").wikidataId(QID).build()));

        assertThat(service().enrich(ID, URL)).isFalse();

        verify(gateway).sendText("⚠️ Вікіпедія «Океан Ельзи» для «Океан Ельзи»: цей запис уже є як "
                + "<a href=\"https://nashi.example/m/okean-elzy-1994\">/m/okean-elzy-1994</a>");
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(Artist.class));
        verifyNoInteractions(events);
        assertThat(asked).isEmpty();
    }

    @Test
    void escapesTheDuplicateMessage() {
        submitted.setName("<b>Гурт</b> & Ко");
        when(artists.findByWikidataId(QID)).thenReturn(Optional.of(Artist.builder().id("other").slug("hurt")
                .wikidataId(QID).build()));

        service().enrich(ID, "https://uk.wikipedia.org/wiki/A%26B");

        verify(gateway).sendText("⚠️ Вікіпедія «A&amp;B» для «&lt;b&gt;Гурт&lt;/b&gt; &amp; Ко»: цей запис уже є як "
                + "<a href=\"https://nashi.example/m/hurt\">/m/hurt</a>");
    }

    @Test
    void theSameArtistMayBeEnrichedAgainAndNothingNewChangesNothing() {
        when(artists.findByWikidataId(QID)).thenReturn(Optional.of(submitted));
        service().enrich(ID, URL);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(Artist.class));
        Document set = set(update.getValue());
        Artist enriched = Artist.builder().id(ID).version(4L).name("Океан Ельзи").kind(MusicianKind.BAND)
                .origin(LVIV).pin(PIN).wikidataId(QID).wikiTitle((String) set.get("wikiTitle"))
                .tagline((String) set.get("tagline")).summary((String) set.get("summary"))
                .description((String) set.get("description")).textSource((TextSource) set.get("textSource"))
                .dates((LifeDates) set.get("dates")).genres(castList(set.get("genres")))
                .works(castList(set.get("works"))).links(castList(set.get("links")))
                .youtubeChannelUrl((String) set.get("youtubeChannelUrl")).images(castList(set.get("images"))).build();
        when(artists.findById(ID)).thenReturn(Optional.of(enriched));
        when(artists.findByWikidataId(QID)).thenReturn(Optional.of(enriched));

        assertThat(service().enrich(ID, URL)).isFalse();

        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(Artist.class));
        verify(events, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void nothingNewFromAnArtistWithoutLinksOrPhotosSetsNoEmptyLists() {
        submitted = Artist.builder().id(ID).version(0L).name("Гурт").kind(MusicianKind.BAND).wikidataId(QID).build();
        when(artists.findById(ID)).thenReturn(Optional.of(submitted));
        found = TestArtists.band(QID, "Гурт").wikiTitle(null).build();

        assertThat(service().enrich(ID, URL)).isFalse();

        verifyNoInteractions(mongo, events);
    }

    @Test
    void englishArticlesAreLookedUpOnTheEnglishWikipedia() {
        service().enrich(ID, "https://en.wikipedia.org/wiki/Okean_Elzy#History");

        assertThat(requests).containsExactly("https://en.wikipedia.org/w/api.php?action=query&format=json"
                + "&formatversion=2&prop=pageprops&ppprop=wikibase_item&redirects=1&titles=Okean Elzy");
        assertThat(asked).extracting(WikiCandidate::qid).containsExactly(QID);
    }

    @Test
    void acceptsOnlyUkrainianAndEnglishWikipedia() {
        WikipediaEnrichmentService service = service();

        for (String url : List.of("https://ru.wikipedia.org/wiki/X", "https://uk.m.wikipedia.org/wiki/X",
                "https://uk.wikipedia.org.evil.example/wiki/X", "ftp://uk.wikipedia.org/wiki/X",
                "https://uk.wikipedia.org/w/index.php?title=X", "https://uk.wikipedia.org/wiki/", "not a url")) {
            assertThat(service.enrich(ID, url)).as(url).isFalse();
        }

        assertThat(requests).isEmpty();
        verifyNoInteractions(mongo, events, gateway);
    }

    @Test
    void anArticleWithoutAWikidataItemChangesNothing() {
        pageprops = "{\"batchcomplete\":true,\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Нема\",\"missing\":true}]}}";

        assertThat(service().enrich(ID, URL)).isFalse();

        assertThat(asked).isEmpty();
        verifyNoInteractions(mongo, events);
    }

    @Test
    void nothingFoundOnWikidataChangesNothing() {
        found = null;

        assertThat(service().enrich(ID, URL)).isFalse();

        verifyNoInteractions(mongo, events);
    }

    @Test
    void appliesAgainWhenTheArtistChangedMeanwhile() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Artist.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null), UpdateResult.acknowledged(1, 1L, null));
        Artist hidden = Artist.builder().id(ID).version(4L).name("Океан Ельзи").kind(MusicianKind.BAND)
                .status(ArtistStatus.HIDDEN).origin(LVIV).pin(PIN).images(List.of(USER_PHOTO)).build();
        when(artists.findById(ID)).thenReturn(Optional.of(submitted), Optional.of(hidden));

        assertThat(service().enrich(ID, URL)).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).updateFirst(query.capture(), any(Update.class), eq(Artist.class));
        assertThat(query.getAllValues().get(1).getQueryObject().get("version")).isEqualTo(4L);
        verify(events).publishEvent(new ArtistChangedEvent(ID));
    }

    @Test
    void aWikidataIdTakenMeanwhileIsReportedLikeADuplicate() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Artist.class)))
                .thenThrow(new DuplicateKeyException("E11000 wikidataId_unique_sparse"));
        when(artists.findByWikidataId(QID)).thenReturn(Optional.empty(), Optional.of(Artist.builder().id("other")
                .slug("okean-elzy-1994").wikidataId(QID).build()));

        assertThat(service().enrich(ID, URL)).isFalse();

        verify(gateway).sendText(anyString());
        verifyNoInteractions(events);
    }

    @Test
    void listensOnTheJobsExecutorAndOnlyWhenEnabled() throws NoSuchMethodException {
        assertThat(WikipediaEnrichmentService.class.getMethod("onRequested", EnrichmentRequested.class)
                .getAnnotation(Async.class).value()).isEqualTo("jobsExecutor");

        service(false).onRequested(new EnrichmentRequested(ID, URL));
        assertThat(requests).isEmpty();

        service(true).onRequested(new EnrichmentRequested(ID, URL));
        assertThat(requests).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return (List<T>) value;
    }
}
