package com.nashi.wiki;

import com.nashi.domain.ArtistWork;
import com.nashi.domain.SocialLink;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WikiDryRunTest {

    private static final WikiPlace LVIV = new WikiPlace("Q36036", "Львів", 49.84, 24.03, Region.LVIV);

    @Test
    void statsCountKindsPlacesDatesWorksPhotosAndLinks() {
        List<WikiArtistData> artists = List.of(
                artist("Q1", List.of(ArtistCategory.VISUAL_ARTIST, ArtistCategory.WRITER), LVIV, LocationBasis.BIRTH,
                        "1814-03-09", "photo.jpg", List.of(work()), List.of(SocialLink.Platform.WIKI,
                                SocialLink.Platform.WIKIDATA, SocialLink.Platform.WIKIART)),
                artist("Q2", List.of(ArtistCategory.BAND), LVIV, LocationBasis.FORMATION, null, null, List.of(),
                        List.of(SocialLink.Platform.WIKIDATA, SocialLink.Platform.SPOTIFY)),
                artist("Q3", List.of(ArtistCategory.WRITER), null, null, "1871", null, List.of(work()),
                        List.of(SocialLink.Platform.WIKIDATA)));

        List<String> lines = WikiDryRun.stats(artists, 5);

        assertTrue(lines.contains("assembled: 3"), lines.toString());
        assertTrue(lines.contains("per category: {MUSICIAN=0, BAND=1, VISUAL_ARTIST=1, WRITER=2}"), lines.toString());
        assertTrue(lines.contains("with a place on the map: 2, by basis: {BIRTH=1, FORMATION=1, RESIDENCE=0, WORK=0}"),
                lines.toString());
        assertTrue(lines.contains("with a birth date: 2"), lines.toString());
        assertTrue(lines.contains("with works: {MUSICIAN=0, BAND=0, VISUAL_ARTIST=1, WRITER=2}"), lines.toString());
        assertTrue(lines.contains("with a photo: 1, with a biography: 0"), lines.toString());
        String links = lines.stream().filter(l -> l.startsWith("links per platform: ")).findFirst().orElseThrow();
        // every platform is listed, as every category is, so a platform nobody has shows as 0
        assertTrue(links.startsWith("links per platform: {INSTAGRAM=0, FACEBOOK=0, SPOTIFY=1, APPLE_MUSIC=0,"), links);
        assertTrue(links.contains(", WIKI=1, ") && links.contains(", WIKIDATA=3, ") && links.endsWith(", WIKIART=1}"),
                links);
        assertEquals(SocialLink.Platform.values().length, links.split("=").length - 1, links);
        assertTrue(lines.contains("excluded as linked to Russia: 5"), lines.toString());
        assertTrue(lines.contains("per region: {LVIV=2}"), lines.toString());
    }

    private static ArtistWork work() {
        return ArtistWork.builder().title("Твір").build();
    }

    private static WikiArtistData artist(String qid, List<ArtistCategory> categories, WikiPlace place,
                                         LocationBasis basis, String birthDate, String imageUrl, List<ArtistWork> works,
                                         List<SocialLink.Platform> platforms) {
        List<SocialLink> links = platforms.stream()
                .map(p -> SocialLink.builder().platform(p).url("https://example.org/" + p).build())
                .toList();
        return new WikiArtistData(qid, "Митець " + qid, null, categories.contains(ArtistCategory.BAND), null, null,
                Set.of(), null, null, place, null, links, imageUrl, null, categories, birthDate, null, null, basis,
                List.of(), works);
    }
}
