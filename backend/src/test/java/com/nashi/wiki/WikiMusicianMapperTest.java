package com.nashi.wiki;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Discipline;
import com.nashi.artist.ImageAsset;
import com.nashi.artist.ImageCredit;
import com.nashi.artist.LifeDates;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Names;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.artist.TextSource;
import com.nashi.artist.Work;
import com.nashi.artist.WorkType;
import com.nashi.geo.PinPlacer;
import com.nashi.geo.PlaceRef;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WikiMusicianMapperTest {

    private static final String ID = "665f1c2e9b1e4a0001a1b2c3";
    private static final String MOSCOW = "Q649";
    private static final String KYIV = "Q1899";
    private static final String LVIV = "Q36036";

    /** Places as Wikidata has them: Kyiv is a region itself, Lviv lies in the Lviv oblast, Moscow in Russia. */
    private final Map<String, WikiEntity> world = new HashMap<>(Map.of(
            KYIV, TestEntities.place(KYIV, "Київ", 50.45, 30.52, "Q212"),
            LVIV, TestEntities.place(LVIV, "Львів", 49.84, 24.03, "Q164193"),
            MOSCOW, TestEntities.place(MOSCOW, "Москва", 55.75, 37.62, "Q159"),
            "Q159", TestEntities.place("Q159", "Росія", 60.0, 90.0)));
    private final PlaceResolver resolver = new PlaceResolver((Collection<String> ids) ->
            ids.stream().filter(world::containsKey).collect(Collectors.toMap(id -> id, world::get)));

    /** The item through the resolver and the assembler, as the collector does it. */
    private WikiArtistData assembled(WikiEntity item, CandidateGroup group) {
        Map<String, WikiPlace> places = resolver.resolve(List.of(KYIV, LVIV, MOSCOW));
        return WikiArtistAssembler.assemble(item, group, places, Map.of(),
                OccupationCategories.fromMusicianTree(OccupationCategories.MUSICIAN_ROOTS), resolver::label, Set.of())
                .orElseThrow();
    }

    /** The place {@code PlaceStore.ensure} makes from a place the seed does not have. */
    private static Place ensured(WikiPlace p) {
        return new Place(p.qid(), p.labelUk(), Names.normalize(p.labelUk()), PlaceType.OTHER, p.region(),
                new GeoJsonPoint(p.lon(), p.lat()), null, null);
    }

    private static Artist map(WikiArtistData data) {
        WikiPlace origin = WikiMusicianMapper.originPlace(data);
        return WikiMusicianMapper.toArtist(data, origin == null ? null : ensured(origin), "slug", ID);
    }

    @Test
    void personOriginIsBirthPlaceOnly() {
        WikiArtistData singer = assembled(TestEntities.person("Q10", "Співачка", Set.of("Q212"), List.of(MOSCOW),
                List.of(KYIV), List.of()), CandidateGroup.MUSIC);
        assertThat(singer.locationBasis()).as("the assembler still falls back to the residence")
                .isEqualTo(LocationBasis.RESIDENCE);

        assertThat(WikiMusicianMapper.originPlace(singer)).isNull();
        Artist artist = WikiMusicianMapper.toArtist(singer, ensured(singer.place()), "spivachka", ID);

        assertThat(artist.getOrigin()).isNull();
        assertThat(artist.getPin()).isNull();
        assertThat(artist.getStatus()).as("saved and published, only not on the map").isEqualTo(ArtistStatus.PUBLISHED);
    }

    @Test
    void personBornInUkraineIsOnTheMapByBirth() {
        WikiArtistData singer = assembled(TestEntities.person("Q11", "Співак", Set.of("Q212"), List.of(LVIV),
                List.of(KYIV), List.of()), CandidateGroup.MUSIC);

        Artist artist = map(singer);

        assertThat(artist.getOrigin().placeId()).isEqualTo(LVIV);
        assertThat(artist.getOrigin().basis()).isEqualTo(OriginBasis.BIRTH);
        assertThat(artist.getOrigin().precision()).isEqualTo(PinPrecision.SETTLEMENT);
    }

    @Test
    void bandOriginIsFormationPlace() {
        WikiArtistData band = assembled(TestEntities.entity("Q20").labelUk("Гурт").ukTitle("Гурт (гурт)")
                .instanceOf(Set.of("Q5741069")).formationPlaces(List.of(LVIV)).build(), CandidateGroup.ENSEMBLE);
        Place lviv = ensured(WikiMusicianMapper.originPlace(band));

        Artist artist = WikiMusicianMapper.toArtist(band, lviv, "hurt", ID);

        assertThat(artist.getKind()).isEqualTo(MusicianKind.BAND);
        assertThat(artist.getOrigin()).isEqualTo(new Origin(LVIV, "Львів", Region.LVIV, OriginBasis.FORMATION,
                PinPrecision.SETTLEMENT, new GeoJsonPoint(24.03, 49.84)));
        assertThat(artist.getPin()).isEqualTo(PinPlacer.pinFor(ID, PlaceRef.of(lviv), PinPrecision.SETTLEMENT));
    }

    @Test
    void bandWithoutAFormationPlaceIsOffTheMap() {
        WikiArtistData band = TestArtists.band("Q21", "Гурт").place(
                new WikiPlace(LVIV, "Львів", 49.84, 24.03, Region.LVIV), LocationBasis.RESIDENCE).build();

        assertThat(WikiMusicianMapper.originPlace(band)).isNull();
        assertThat(map(band).getOrigin()).isNull();
    }

    @Test
    void kyivMapsToKyivCity() {
        WikiArtistData singer = assembled(TestEntities.person("Q12", "Київська співачка", Set.of("Q212"),
                List.of(KYIV), List.of(), List.of()), CandidateGroup.MUSIC);

        Artist artist = map(singer);

        assertThat(artist.getOrigin().placeId()).isEqualTo(KYIV);
        assertThat(artist.getOrigin().region()).isEqualTo(Region.KYIV_CITY);
        assertThat(artist.getOrigin().basis()).isEqualTo(OriginBasis.BIRTH);
    }

    @Test
    void mapsNewLinkProperties() {
        WikiArtistData band = assembled(TestEntities.entity("Q22").labelUk("Гурт").ukTitle("Гурт")
                .instanceOf(Set.of("Q5741069")).socialIds(Map.of(
                        "P2850", "1029409", "P2722", "13612387", "P2397", "UCjJ-oj2aJTINILYLDv2MFEQ",
                        "P434", "0b1c2d3e-4f50-4a61-8b72-93a4b5c6d7e8", "P1953", "123")).build(),
                CandidateGroup.ENSEMBLE);

        Artist artist = map(band);

        assertThat(artist.getLinks()).containsExactly(
                new Link(LinkPlatform.YOUTUBE, "https://www.youtube.com/channel/UCjJ-oj2aJTINILYLDv2MFEQ"),
                new Link(LinkPlatform.APPLE_MUSIC, "https://music.apple.com/artist/1029409"),
                new Link(LinkPlatform.DEEZER, "https://www.deezer.com/artist/13612387"),
                new Link(LinkPlatform.OTHER, "https://www.discogs.com/artist/123"),
                new Link(LinkPlatform.WIKIPEDIA, "https://uk.wikipedia.org/wiki/%D0%93%D1%83%D1%80%D1%82"));
        assertThat(artist.getYoutubeChannelUrl()).isEqualTo("https://www.youtube.com/channel/UCjJ-oj2aJTINILYLDv2MFEQ");
    }

    @Test
    void aYoutubeHandleFromMusicBrainzIsALinkButNotTheChannel() {
        WikiArtistData band = TestArtists.band("Q23", "Гурт")
                .link(WikiLink.Platform.MUSICBRAINZ, "https://musicbrainz.org/artist/0b1c2d3e")
                .link(WikiLink.Platform.YOUTUBE, "https://www.youtube.com/@hurt")
                .build();

        Artist artist = map(band);

        assertThat(artist.getLinks()).containsExactly(new Link(LinkPlatform.YOUTUBE, "https://www.youtube.com/@hurt"));
        assertThat(artist.getYoutubeChannelUrl()).isNull();
    }

    @Test
    void textSourceIsCcBySa() {
        WikiArtistData singer = TestArtists.musician("Q30", "Соломія Крушельницька")
                .wikiTitle("Крушельницька Соломія Амвросіївна")
                .biography("Соломія Амвросіївна Крушельницька (нар. 23 вересня 1872) — українська оперна співачка.")
                .build();

        Artist artist = map(singer);

        assertThat(artist.getSummary()).isEqualTo("Соломія Амвросіївна Крушельницька — українська оперна співачка.");
        assertThat(artist.getDescription()).isEqualTo(artist.getSummary());
        assertThat(artist.getTextSource()).isEqualTo(new TextSource("WIKIPEDIA", "Крушельницька Соломія Амвросіївна",
                "https://uk.wikipedia.org/wiki/"
                        + URLEncoder.encode("Крушельницька_Соломія_Амвросіївна", StandardCharsets.UTF_8),
                "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/deed.uk"));
    }

    @Test
    void noIntroMeansNoTextAndNoTextSource() {
        Artist artist = map(TestArtists.musician("Q31", "Без статті").build());

        assertThat(artist.getSummary()).isNull();
        assertThat(artist.getDescription()).isNull();
        assertThat(artist.getTextSource()).isNull();
    }

    @Test
    void mapsThePublishedWikipediaMusician() {
        WikiArtistData singer = TestArtists.musician("Q40", "Джамала").tagline("українська співачка")
                .birthDate("1983-08-27").years(2001, null).genres(List.of("джаз", "соул"))
                .works(List.of(new WikiWork("Q22132106", "Подих", 2015, null, null, null,
                        "https://uk.wikipedia.org/wiki/Podykh"), new WikiWork("Q1", "Без статті", null, null, null,
                        null, null)))
                .photo("Jamala.jpg", "Автор", "CC BY-SA 4.0")
                .build();

        Artist artist = WikiMusicianMapper.toArtist(singer, null, "dzhamala", ID);

        assertThat(artist.getId()).isEqualTo(ID);
        assertThat(artist.getSlug()).isEqualTo("dzhamala");
        assertThat(artist.getName()).isEqualTo("Джамала");
        assertThat(artist.getSource()).isEqualTo(ArtistSource.WIKIPEDIA);
        assertThat(artist.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(artist.getDisciplines()).containsExactly(Discipline.MUSIC);
        assertThat(artist.getKind()).isEqualTo(MusicianKind.PERSON);
        assertThat(artist.getWikidataId()).isEqualTo("Q40");
        assertThat(artist.getWikiTitle()).isEqualTo("Джамала");
        assertThat(artist.getTagline()).isEqualTo("українська співачка");
        assertThat(artist.getDates()).isEqualTo(new LifeDates("1983-08-27", null, "2001", null));
        assertThat(artist.getGenres()).containsExactly("джаз", "соул");
        assertThat(artist.getWorks()).containsExactly(
                new Work("Подих", 2015, WorkType.ALBUM, "https://uk.wikipedia.org/wiki/Podykh"),
                new Work("Без статті", null, WorkType.ALBUM, null));
        assertThat(artist.getImages()).containsExactly(new ImageAsset(null,
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Jamala.jpg/800px-Jamala.jpg", null,
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Jamala.jpg/400px-Jamala.jpg", "Джамала",
                new ImageCredit("Автор", "CC BY-SA 4.0", null, "https://commons.wikimedia.org/wiki/File:Jamala.jpg")));
        assertThat(artist.getLinks()).isEmpty();
    }

    @Test
    void noPhotoMeansNoImages() {
        assertThat(map(TestArtists.musician("Q41", "Без фото").build()).getImages()).isEmpty();
    }
}
