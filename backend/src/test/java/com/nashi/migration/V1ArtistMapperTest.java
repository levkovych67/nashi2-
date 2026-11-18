package com.nashi.migration;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistSource;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.geo.Region;
import com.nashi.place.Place;
import com.nashi.place.PlaceType;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class V1ArtistMapperTest {

    private static final Place LVIV = new Place("Q36036", "Львів", "львів", PlaceType.CITY, Region.LVIV,
            new GeoJsonPoint(24.03, 49.84), 182.0, 717_273L);

    private static Document form(String name) {
        return new Document("_id", "65a000000000000000000001").append("name", name).append("status", "APPROVED")
                .append("biography", "Гурт з Львова.").append("style", "рок").append("region", "LVIV")
                .append("city", "Львів");
    }

    @Test
    void mapsFormArtistWithMembersAsBand() {
        Document v1 = form("Тестовий Гурт").append("members", List.of(new Document("name", "Іван")));

        Artist artist = V1ArtistMapper.map(v1, LVIV, "a1", "testovyi-hurt");

        assertThat(artist.getId()).isEqualTo("a1");
        assertThat(artist.getSlug()).isEqualTo("testovyi-hurt");
        assertThat(artist.getLegacyV1Id()).isEqualTo("65a000000000000000000001");
        assertThat(artist.getSource()).isEqualTo(ArtistSource.V1_MIGRATION);
        assertThat(artist.getStatus()).isEqualTo(ArtistStatus.PUBLISHED);
        assertThat(artist.getKind()).isEqualTo(MusicianKind.BAND);
        assertThat(artist.getSummary()).isEqualTo("Гурт з Львова.");
        assertThat(artist.getGenres()).containsExactly("рок");
        assertThat(artist.getOrigin().placeId()).isEqualTo("Q36036");
        assertThat(artist.getOrigin().basis()).isEqualTo(OriginBasis.ORIGIN);
        assertThat(artist.getOrigin().precision()).isEqualTo(PinPrecision.CITY);
        assertThat(artist.getPin()).isNotNull();
    }

    @Test
    void bandCategoryWithoutMembersIsBandAndSoloIsPerson() {
        assertThat(V1ArtistMapper.map(form("Гурт").append("categories", List.of("BAND")), LVIV, "a", "s").getKind())
                .isEqualTo(MusicianKind.BAND);
        assertThat(V1ArtistMapper.map(form("Соліст").append("categories", List.of("MUSICIAN")), null, "a", "s")
                .getKind()).isEqualTo(MusicianKind.PERSON);
    }

    @Test
    void longBiographyIsTruncatedAndNoPlaceMeansNoPin() {
        Artist artist = V1ArtistMapper.map(form("Довгий").append("biography", "слово ".repeat(400)), null, "a", "s");

        assertThat(artist.getSummary()).hasSizeLessThanOrEqualTo(1001).endsWith("…");
        assertThat(artist.getOrigin()).isNull();
        assertThat(artist.getPin()).isNull();
    }

    @Test
    void kyivCityResolvesToKyivCity() {
        assertThat(V1ArtistMapper.regionOf(form("A").append("region", "KYIV").append("city", "Київ")))
                .isEqualTo(Region.KYIV_CITY);
        assertThat(V1ArtistMapper.regionOf(form("A").append("region", "KYIV").append("city", "Бровари")))
                .isEqualTo(Region.KYIV);
    }

    @Test
    void sevastopolResolvesToSevastopolCityAndUnknownRegionIsNull() {
        assertThat(V1ArtistMapper.regionOf(form("A").append("region", "CRIMEA").append("city", "Севастополь")))
                .isEqualTo(Region.SEVASTOPOL_CITY);
        assertThat(V1ArtistMapper.regionOf(form("A").append("region", "CRIMEA").append("city", "Ялта")))
                .isEqualTo(Region.CRIMEA);
        assertThat(V1ArtistMapper.regionOf(form("A").append("region", "ATLANTIS"))).isNull();
        assertThat(V1ArtistMapper.regionOf(new Document("name", "A"))).isNull();
    }

    @Test
    void unknownV1PlatformBecomesOther() {
        Document v1 = form("Лінки").append("socialLinks", List.of(
                new Document("platform", "SPOTIFY").append("url", "https://open.spotify.com/artist/1"),
                new Document("platform", "WIKI").append("url", "https://uk.wikipedia.org/wiki/X"),
                new Document("platform", "DISCOGS").append("url", "https://discogs.com/artist/1"),
                new Document("platform", "NEW_V1_THING").append("url", "https://example.com/x"),
                new Document("platform", "INSTAGRAM").append("url", " ")));

        assertThat(V1ArtistMapper.map(v1, LVIV, "a", "s").getLinks()).containsExactly(
                new Link(LinkPlatform.SPOTIFY, "https://open.spotify.com/artist/1"),
                new Link(LinkPlatform.WIKIPEDIA, "https://uk.wikipedia.org/wiki/X"),
                new Link(LinkPlatform.OTHER, "https://discogs.com/artist/1"),
                new Link(LinkPlatform.OTHER, "https://example.com/x"));
    }

    @Test
    void keepsOnlyAbsoluteHttpLinksLikeSubmissions() {
        Document v1 = form("Лінки2").append("socialLinks", List.of(
                new Document("platform", "SPOTIFY").append("url", " https://open.spotify.com/artist/1 "),
                new Document("platform", "WEBSITE").append("url", "http://band.example"),
                new Document("platform", "INSTAGRAM").append("url", "javascript:alert(1)"),
                new Document("platform", "FACEBOOK").append("url", "facebook.com/band"),
                new Document("platform", "TELEGRAM").append("url", "tg://resolve?domain=band"),
                new Document("platform", "OTHER").append("url", "mailto:band@example.com"),
                new Document("platform", "OTHER").append("url", "https://"),
                new Document("platform", "OTHER").append("url", "https://bad host/x")));

        assertThat(V1ArtistMapper.map(v1, LVIV, "a", "s").getLinks()).containsExactly(
                new Link(LinkPlatform.SPOTIFY, "https://open.spotify.com/artist/1"),
                new Link(LinkPlatform.WEBSITE, "http://band.example"));
    }

    @Test
    void onlyVisualArtistsAndWritersAreNonMusic() {
        assertThat(V1ArtistMapper.isNonMusic(new Document("categories", List.of("VISUAL_ARTIST")))).isTrue();
        assertThat(V1ArtistMapper.isNonMusic(new Document("categories", List.of("WRITER", "VISUAL_ARTIST")))).isTrue();
        assertThat(V1ArtistMapper.isNonMusic(new Document("categories", List.of("WRITER", "MUSICIAN")))).isFalse();
        assertThat(V1ArtistMapper.isNonMusic(new Document("categories", List.of("BAND")))).isFalse();
        assertThat(V1ArtistMapper.isNonMusic(new Document("categories", List.of()))).isFalse();
        assertThat(V1ArtistMapper.isNonMusic(new Document("name", "no categories at all"))).isFalse();
    }
}
