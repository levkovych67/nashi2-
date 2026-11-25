package com.nashi.wiki;

import com.nashi.domain.SocialLink;
import com.nashi.domain.SocialLink.Platform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicBrainzParserTest {

    private static String rels(String... urls) {
        StringBuilder sb = new StringBuilder("{\"relations\":[");
        for (int i = 0; i < urls.length; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"type\":\"x\",\"url\":{\"resource\":\"").append(urls[i]).append("\"}}");
        }
        return sb.append("]}").toString();
    }

    private static List<String> urls(List<SocialLink> links, Platform platform) {
        return links.stream().filter(l -> l.getPlatform() == platform).map(SocialLink::getUrl).toList();
    }

    @Test
    void parsesKnownHostsOnly() {
        List<SocialLink> links = MusicBrainzParser.links(Fixtures.read("musicbrainz-url-rels.json"));

        assertEquals(List.of("https://open.spotify.com/artist/0VFXuZuPmwQ3YyLyuLBhF7"), urls(links, Platform.SPOTIFY));
        assertEquals(List.of("https://music.apple.com/us/artist/1605995193"), urls(links, Platform.APPLE_MUSIC));
        assertEquals(List.of("https://www.deezer.com/artist/309548"), urls(links, Platform.DEEZER));
        assertEquals(List.of("https://naturalspirit.bandcamp.com/"), urls(links, Platform.BANDCAMP));
        assertEquals("https://www.youtube.com/channel/UCIBrOaQCEcmtUGyanu360Xg", urls(links, Platform.YOUTUBE).get(0));
        // discogs, vk, facebook, twitter, tidal, the homepage and the rest are not music platforms for us
        assertEquals(6, links.size(), links.toString());
    }

    @Test
    void normalisesYoutubeMusicChannel() {
        List<SocialLink> links = MusicBrainzParser.links(
                rels("https://music.youtube.com/channel/UC3wO9-AUG8Pzs0DohfEDVFw", "https://music.youtube.com/playlist/x"));

        assertEquals(List.of("https://www.youtube.com/channel/UC3wO9-AUG8Pzs0DohfEDVFw"), urls(links, Platform.YOUTUBE));
    }

    @Test
    void keepsHandlesAndNamesAndPutsChannelsFirst() {
        List<SocialLink> links = MusicBrainzParser.links(rels(
                "https://www.youtube.com/@someone", "https://www.youtube.com/user/old", "https://youtube.com/channel/UCabc"));

        assertEquals(3, urls(links, Platform.YOUTUBE).size());
        assertEquals("https://www.youtube.com/channel/UCabc", urls(links, Platform.YOUTUBE).get(0));
        assertTrue(urls(links, Platform.YOUTUBE).contains("https://www.youtube.com/@someone"));
        assertTrue(urls(links, Platform.YOUTUBE).contains("https://www.youtube.com/user/old"));
    }

    @Test
    void stripsQuery() {
        List<SocialLink> links = MusicBrainzParser.links(rels(
                "https://open.spotify.com/artist/abc?si=xyz", "https://soundcloud.com/band?ref=1#top",
                "https://vk.com/band", "https://vk.ru/band"));

        assertEquals(List.of("https://open.spotify.com/artist/abc"), urls(links, Platform.SPOTIFY));
        assertEquals(List.of("https://soundcloud.com/band"), urls(links, Platform.SOUNDCLOUD));
        assertEquals(2, links.size());
    }
}
