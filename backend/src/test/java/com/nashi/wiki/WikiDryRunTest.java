package com.nashi.wiki;

import com.nashi.geo.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WikiDryRunTest {

    private static final WikiPlace LVIV = new WikiPlace("Q36036", "Львів", 49.84, 24.03, Region.LVIV);
    private static final WikiPlace KYIV = new WikiPlace("Q1899", "Київ", 50.45, 30.52, Region.KYIV_CITY);

    @Test
    void statsCountGroupsPlacesTaglinesDatesAlbumsPhotosAndLinks() {
        List<WikiCandidate> checked = List.of(new WikiCandidate("Q1", "Співачка", CandidateGroup.MUSIC),
                new WikiCandidate("Q2", "Гурт", CandidateGroup.ENSEMBLE),
                new WikiCandidate("Q3", "Співак", CandidateGroup.MUSIC),
                new WikiCandidate("Q4", "Росіянин", CandidateGroup.MUSIC));
        List<WikiArtistData> artists = List.of(
                TestArtists.musician("Q1", "Співачка").place(LVIV, LocationBasis.BIRTH).tagline("співачка")
                        .birthDate("1983-08-27").photo("a.jpg", "Автор", "CC0").biography("Співачка — співачка.")
                        .youtubeChannelUrl("https://www.youtube.com/channel/UC1")
                        .link(WikiLink.Platform.WIKI, "https://uk.wikipedia.org/wiki/a")
                        .link(WikiLink.Platform.WIKIDATA, "https://www.wikidata.org/wiki/Q1")
                        .link(WikiLink.Platform.YOUTUBE, "https://www.youtube.com/channel/UC1").build(),
                TestArtists.band("Q2", "Гурт").place(LVIV, LocationBasis.FORMATION)
                        .works(List.of(new WikiWork("Q20", "Альбом", 2001, null, null, null, null)))
                        .link(WikiLink.Platform.WIKIDATA, "https://www.wikidata.org/wiki/Q2")
                        .link(WikiLink.Platform.SPOTIFY, "https://open.spotify.com/artist/x").build(),
                TestArtists.musician("Q3", "Співак").place(KYIV, LocationBasis.RESIDENCE).birthDate("1871")
                        .link(WikiLink.Platform.WIKIDATA, "https://www.wikidata.org/wiki/Q3").build());

        List<String> lines = WikiDryRun.stats(checked, artists, 1);

        assertThat(lines).contains(
                "checked per group: {MUSIC=3, ENSEMBLE=1}",
                "assembled: 3",
                "per category: {MUSICIAN=2, BAND=1}",
                "with a place in Ukraine: 3 (100%), by basis: {BIRTH=1, FORMATION=1, RESIDENCE=1, WORK=0}",
                "on the map (birth place of a person, formation place of a band): 2 (67%)",
                "per region on the map: {LVIV=2}",
                "with a tagline: 1",
                "with a birth date: 2",
                "with albums: 1",
                "with a photo: 1, with a biography: 1",
                "with a YouTube channel: 1",
                "excluded as linked to Russia: 1");
        String links = lines.stream().filter(l -> l.startsWith("links per platform: ")).findFirst().orElseThrow();
        // every platform is listed, so a platform nobody has shows as 0
        assertThat(links).startsWith("links per platform: {INSTAGRAM=0, FACEBOOK=0, SPOTIFY=1, APPLE_MUSIC=0,")
                .contains(", YOUTUBE=1, ", ", WIKI=1, ", ", WIKIDATA=3");
        assertThat(links.split("=").length - 1).isEqualTo(WikiLink.Platform.values().length);
    }

    @Test
    void noArtistsIsNoDivisionByZero() {
        assertThat(WikiDryRun.stats(List.of(), List.of(), 0))
                .contains("with a place in Ukraine: 0 (0%), by basis: {BIRTH=0, FORMATION=0, RESIDENCE=0, WORK=0}");
    }

    @Test
    void limitArgument() {
        assertThat(WikiDryRun.limit(new String[]{"--limit", "50"})).isEqualTo(50);
        assertThat(WikiDryRun.limit(new String[]{})).isZero();
    }
}
