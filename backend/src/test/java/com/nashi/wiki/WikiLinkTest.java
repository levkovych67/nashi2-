package com.nashi.wiki;

import com.nashi.artist.LinkPlatform;
import com.nashi.wiki.WikiLink.Platform;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiLinkTest {

    @Test
    void platformsOfTheApiKeepTheirName() {
        for (Platform platform : new Platform[] {Platform.INSTAGRAM, Platform.FACEBOOK, Platform.SPOTIFY,
                Platform.APPLE_MUSIC, Platform.YOUTUBE, Platform.SOUNDCLOUD, Platform.TIKTOK, Platform.TELEGRAM,
                Platform.WEBSITE, Platform.X, Platform.DEEZER, Platform.BANDCAMP, Platform.OTHER}) {
            assertEquals(Optional.of(LinkPlatform.valueOf(platform.name())), platform.toV2(), platform.name());
        }
    }

    @Test
    void wikiIsWikipedia() {
        assertEquals(Optional.of(LinkPlatform.WIKIPEDIA), Platform.WIKI.toV2());
    }

    @Test
    void discogsLastfmAndEsuAreOther() {
        assertEquals(Optional.of(LinkPlatform.OTHER), Platform.DISCOGS.toV2());
        assertEquals(Optional.of(LinkPlatform.OTHER), Platform.LASTFM.toV2());
        assertEquals(Optional.of(LinkPlatform.OTHER), Platform.ESU.toV2());
    }

    @Test
    void platformsWithoutACounterpartAreDropped() {
        for (Platform platform : new Platform[] {Platform.WIKIDATA, Platform.MUSICBRAINZ, Platform.COMMONS,
                Platform.WIKISOURCE, Platform.OPEN_LIBRARY, Platform.GOODREADS, Platform.CHTYVO, Platform.ARTSY,
                Platform.WIKIART}) {
            assertTrue(platform.toV2().isEmpty(), platform.name());
        }
    }

    @Test
    void everyPlatformIsDecided() {
        for (Platform platform : Platform.values()) {
            platform.toV2();
        }
    }
}
