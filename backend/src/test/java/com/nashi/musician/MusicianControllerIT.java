package com.nashi.musician;

import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.Origin;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.geo.Region;
import com.nashi.support.MongoIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicianControllerIT extends MongoIT {

    @Autowired
    private RestTestClient http;

    @Autowired
    private ArtistRepository artists;

    @BeforeEach
    void seed() {
        artists.deleteAll();
        // NAME order: Без піна, Бенд А, Кам’янець-бенд, Київський гурт
        save("band-a", "Бенд А", Region.LVIV, ArtistStatus.PUBLISHED, true, 5, "2026-01-01T00:00:00Z");
        save("kamianets", "Кам’янець-бенд", Region.LVIV, ArtistStatus.PUBLISHED, true, 50, "2026-03-01T00:00:00Z");
        save("kyiv-one", "Київський гурт", Region.KYIV_CITY, ArtistStatus.PUBLISHED, true, 20, "2026-02-01T00:00:00Z");
        save("hidden", "Прихований", Region.LVIV, ArtistStatus.HIDDEN, true, 99, "2026-04-01T00:00:00Z");
        save("no-pin", "Без піна", Region.ODESA, ArtistStatus.PUBLISHED, false, 1, "2025-12-01T00:00:00Z");
    }

    @AfterEach
    void clear() {
        artists.deleteAll();
    }

    private void save(String slug, String name, Region region, ArtistStatus status, boolean pin, long likes,
                      String publishedAt) {
        artists.save(Artist.builder().slug(slug).name(name).kind(MusicianKind.BAND).status(status)
                .summary("Опис").likesCount(likes).publishedAt(Instant.parse(publishedAt))
                .origin(new Origin("Q1", "Місто", region, OriginBasis.FORMATION, PinPrecision.CITY,
                        new GeoJsonPoint(24, 49)))
                .pin(pin ? new GeoJsonPoint(24.1, 49.1) : null)
                .links(List.of(new Link(LinkPlatform.INSTAGRAM, "https://i"), new Link(LinkPlatform.SPOTIFY, "https://s")))
                .audio(List.of(new AudioClip("c1", AudioSource.UPLOAD, null, "k", "https://play", "T", 10, "a", null)))
                .build());
    }

    private RestTestClient.ResponseSpec get(String uri) {
        return http.get().uri("/api/v2/musicians" + uri).exchange();
    }

    private void expect400(String uri, String field) {
        get(uri).expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field").isEqualTo(field);
    }

    @Test
    void listsOnlyPublished() {
        get("").expectStatus().isOk().expectBody()
                .jsonPath("$.totalItems").isEqualTo(4)
                .jsonPath("$.items.length()").isEqualTo(4)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalPages").isEqualTo(1)
                .jsonPath("$.items[0].thumbUrl").doesNotExist()
                .jsonPath("$.items[0].hasAudio").isEqualTo(true);
    }

    @Test
    void filtersByRepeatedRegion() {
        get("?region=LVIV&region=KYIV_CITY").expectStatus().isOk().expectBody()
                .jsonPath("$.totalItems").isEqualTo(3);
    }

    @Test
    void searchIgnoresCaseApostropheStress() {
        get("?q=КАМЯНЕЦЬ").expectStatus().isOk().expectBody()
                .jsonPath("$.totalItems").isEqualTo(1)
                .jsonPath("$.items[0].slug").isEqualTo("kamianets");
    }

    @Test
    void sortsByLikesDesc() {
        get("?sort=LIKES").expectStatus().isOk().expectBody()
                .jsonPath("$.items[0].slug").isEqualTo("kamianets")
                .jsonPath("$.items[1].slug").isEqualTo("kyiv-one")
                .jsonPath("$.items[3].slug").isEqualTo("no-pin");
    }

    @Test
    void sortsByNameAndRecentAndPages() {
        get("").expectBody().jsonPath("$.items[0].slug").isEqualTo("no-pin")
                .jsonPath("$.items[3].slug").isEqualTo("kyiv-one");
        get("?sort=RECENT").expectBody().jsonPath("$.items[0].slug").isEqualTo("kamianets")
                .jsonPath("$.items[3].slug").isEqualTo("no-pin");
        get("?size=3&page=1").expectBody().jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.totalPages").isEqualTo(2);
    }

    @Test
    void sizeAbove100Is400() {
        expect400("?size=101", "size");
    }

    @Test
    void sizeZeroIs400() {
        expect400("?size=0", "size");
    }

    @Test
    void negativePageIs400() {
        expect400("?page=-1", "page");
    }

    @Test
    void shortQueryIs400() {
        expect400("?q=к", "q");
        expect400("?q=к’", "q");
    }

    @Test
    void unknownRegionIs400() {
        expect400("?region=ATLANTIS", "region");
    }

    @Test
    void unknownSortIs400() {
        expect400("?sort=RANDOM", "sort");
    }

    @Test
    void detailsBySlug() {
        get("/kamianets").expectStatus().isOk().expectBody()
                .jsonPath("$.origin.region").isEqualTo("LVIV")
                .jsonPath("$.origin.regionName").isEqualTo("Львівська область")
                .jsonPath("$.origin.lat").isEqualTo(49.1)
                .jsonPath("$.links[0].group").isEqualTo("MUSIC")
                .jsonPath("$.links[0].platform").isEqualTo("SPOTIFY")
                .jsonPath("$.textSource").value(v -> assertThat(v).isNull())
                .jsonPath("$.materials.clips[0].url").doesNotExist()
                .jsonPath("$.hasAudio").isEqualTo(true);
    }

    @Test
    void hiddenIs404Problem() {
        get("/hidden").expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
        get("/missing").expectStatus().isNotFound();
    }

    @Test
    void musicianWithoutPinHasPage() {
        get("/no-pin").expectStatus().isOk().expectBody()
                .jsonPath("$.origin").value(v -> assertThat(v).isNull());
    }
}
