package com.nashi.wiki;

import com.nashi.domain.SocialLink;
import com.nashi.domain.SocialLink.Platform;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WikiLinkTableTest {

    private final Map<String, WikiEntity> kinds = WikidataEntityParser.parse(Fixtures.read("entities-kinds.json"));

    @Test
    void bandcampTemplate() {
        // P1630 of P3283 is "https://$1.bandcamp.com" (the spec had a trailing slash)
        WikiEntity band = TestEntities.entity("Q1").socialIds(Map.of("P3283", "okeanelzy")).build();

        assertEquals("https://okeanelzy.bandcamp.com", url(WikiLinkTable.links(band), Platform.BANDCAMP));
    }

    @Test
    void commonsCategoryEncoded() {
        assertEquals("https://commons.wikimedia.org/wiki/Category:Taras_Shevchenko",
                url(WikiLinkTable.links(kinds.get("Q134958")), Platform.COMMONS));

        WikiEntity cyrillic = TestEntities.entity("Q2").commonsCategory("Леся Українка").build();
        assertEquals("https://commons.wikimedia.org/wiki/Category:"
                        + "%D0%9B%D0%B5%D1%81%D1%8F_%D0%A3%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D0%BA%D0%B0",
                url(WikiLinkTable.links(cyrillic), Platform.COMMONS));
    }

    @Test
    void wikisourceLink() {
        // "Автор:Тарас Шевченко": spaces become "_", the rest is URL-encoded
        assertEquals("https://uk.wikisource.org/wiki/%D0%90%D0%B2%D1%82%D0%BE%D1%80%3A"
                        + "%D0%A2%D0%B0%D1%80%D0%B0%D1%81_%D0%A8%D0%B5%D0%B2%D1%87%D0%B5%D0%BD%D0%BA%D0%BE",
                url(WikiLinkTable.links(kinds.get("Q134958")), Platform.WIKISOURCE));
        assertNull(url(WikiLinkTable.links(kinds.get("Q4082561")), Platform.WIKISOURCE));
    }

    @Test
    void wikidataAlwaysPresent() {
        List<SocialLink> links = WikiLinkTable.links(TestEntities.entity("Q42").build());

        assertEquals(1, links.size());
        assertEquals(Platform.WIKIDATA, links.get(0).getPlatform());
        assertEquals("https://www.wikidata.org/wiki/Q42", links.get(0).getUrl());
    }

    @Test
    void orderFollowsTable() {
        Map<String, String> ids = new LinkedHashMap<>();
        // deliberately not in table order
        for (String property : List.of("P13437", "P4613", "P6002", "P2042", "P648", "P2963", "P3192", "P434", "P1953",
                "P3283", "P3040", "P2722", "P2850", "P1902", "P2397", "P3789", "P7085", "P2002", "P2013", "P2003",
                "P856")) {
            ids.put(property, "id-" + property);
        }
        ids.put("P856", "https://example.org");
        WikiEntity everything = TestEntities.entity("Q7").ukTitle("Стаття").commonsCategory("Category name")
                .wikisourceTitle("Автор:Хтось").socialIds(ids).build();

        List<Platform> platforms = WikiLinkTable.links(everything).stream().map(SocialLink::getPlatform).toList();

        assertEquals(List.of(Platform.WEBSITE, Platform.INSTAGRAM, Platform.FACEBOOK, Platform.X, Platform.TIKTOK,
                Platform.TELEGRAM, Platform.YOUTUBE, Platform.SPOTIFY, Platform.APPLE_MUSIC, Platform.DEEZER,
                Platform.SOUNDCLOUD, Platform.BANDCAMP, Platform.DISCOGS, Platform.MUSICBRAINZ, Platform.LASTFM,
                Platform.COMMONS, Platform.WIKISOURCE, Platform.GOODREADS, Platform.OPEN_LIBRARY, Platform.ARTSY,
                Platform.WIKIART, Platform.ESU, Platform.CHTYVO, Platform.WIKI, Platform.WIKIDATA), platforms);
    }

    @Test
    void templatesOfEveryPlatform() {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("P2003", "okeanelzy_official");
        ids.put("P2013", "okeanelzyofficial");
        ids.put("P2002", "okeanelzy");
        ids.put("P7085", "okeanelzy");
        ids.put("P3789", "okeanelzy");
        ids.put("P2397", "UCjJ-oj2aJTINILYLDv2MFEQ");
        ids.put("P1902", "6NTzEgUmN1PIBIYEHhf1kS");
        ids.put("P2850", "362306254");
        ids.put("P2722", "4296");
        ids.put("P3040", "okean-elzy");
        ids.put("P1953", "125748");
        ids.put("P434", "87a417d3-f118-43e0-bece-3de1bd90fce0");
        ids.put("P3192", "Океан+Ельзи");
        ids.put("P2963", "135390");
        ids.put("P648", "OL57555A");
        ids.put("P2042", "kateryna-bilokur");
        ids.put("P6002", "kateryna-bilokur");
        ids.put("P4613", "40828");
        ids.put("P13437", "Ukrainka");
        List<SocialLink> links = WikiLinkTable.links(TestEntities.entity("Q8").socialIds(ids).build());

        assertEquals("https://www.instagram.com/okeanelzy_official/", url(links, Platform.INSTAGRAM));
        assertEquals("https://www.facebook.com/okeanelzyofficial", url(links, Platform.FACEBOOK));
        assertEquals("https://x.com/okeanelzy", url(links, Platform.X));
        assertEquals("https://www.tiktok.com/@okeanelzy", url(links, Platform.TIKTOK));
        // The spec's template, not P1630's channel preview "https://t.me/s/$1", which breaks for personal accounts
        assertEquals("https://t.me/okeanelzy", url(links, Platform.TELEGRAM));
        assertEquals("https://www.youtube.com/channel/UCjJ-oj2aJTINILYLDv2MFEQ", url(links, Platform.YOUTUBE));
        assertEquals("https://open.spotify.com/artist/6NTzEgUmN1PIBIYEHhf1kS", url(links, Platform.SPOTIFY));
        assertEquals("https://music.apple.com/artist/362306254", url(links, Platform.APPLE_MUSIC));
        assertEquals("https://www.deezer.com/artist/4296", url(links, Platform.DEEZER));
        assertEquals("https://soundcloud.com/okean-elzy", url(links, Platform.SOUNDCLOUD));
        assertEquals("https://www.discogs.com/artist/125748", url(links, Platform.DISCOGS));
        assertEquals("https://musicbrainz.org/artist/87a417d3-f118-43e0-bece-3de1bd90fce0",
                url(links, Platform.MUSICBRAINZ));
        assertEquals("https://www.last.fm/music/Океан+Ельзи", url(links, Platform.LASTFM));
        assertEquals("https://www.goodreads.com/author/show/135390", url(links, Platform.GOODREADS));
        assertEquals("https://openlibrary.org/authors/OL57555A", url(links, Platform.OPEN_LIBRARY));
        assertEquals("https://www.artsy.net/artist/kateryna-bilokur", url(links, Platform.ARTSY));
        assertEquals("https://www.wikiart.org/en/kateryna-bilokur", url(links, Platform.WIKIART));
        assertEquals("https://esu.com.ua/search_articles.php?id=40828", url(links, Platform.ESU));
        assertEquals("https://chtyvo.org.ua/authors/Ukrainka", url(links, Platform.CHTYVO));
    }

    @Test
    void articleLinkWhenThereIsAUkTitle() {
        assertEquals("https://uk.wikipedia.org/wiki/%D0%9B%D0%B5%D1%81%D1%8F_%D0%A3%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D0%BA%D0%B0",
                url(WikiLinkTable.links(kinds.get("Q298033")), Platform.WIKI));
        assertNull(url(WikiLinkTable.links(TestEntities.entity("Q9").build()), Platform.WIKI));
    }

    private static String url(List<SocialLink> links, Platform platform) {
        return links.stream().filter(l -> l.getPlatform() == platform).map(SocialLink::getUrl).findFirst()
                .orElse(null);
    }
}
