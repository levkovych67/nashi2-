package com.nashi.audio;

import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalIdsTest {

    private static Link link(LinkPlatform platform, String url) {
        return new Link(platform, url);
    }

    @Test
    void extractsDeezerAndAppleIds() {
        List<Link> links = List.of(
                link(LinkPlatform.WEBSITE, "https://example.com"),
                link(LinkPlatform.DEEZER, "https://www.deezer.com/en/artist/13612387"),
                link(LinkPlatform.APPLE_MUSIC, "https://music.apple.com/ua/artist/okean-elzy/1029409"));

        assertThat(ExternalIds.deezerArtistId(links)).contains("13612387");
        assertThat(ExternalIds.appleArtistId(links)).contains("1029409");
    }

    @Test
    void deezerLinkWithoutLanguageSegment() {
        assertThat(ExternalIds.deezerArtistId(List.of(link(LinkPlatform.DEEZER, "https://deezer.com/artist/42"))))
                .contains("42");
    }

    @Test
    void appleLinkWithoutSlugAndLegacyItunesLink() {
        assertThat(ExternalIds.appleArtistId(List.of(link(LinkPlatform.APPLE_MUSIC, "https://music.apple.com/artist/77"))))
                .contains("77");
        assertThat(ExternalIds.appleArtistId(List.of(
                link(LinkPlatform.APPLE_MUSIC, "https://itunes.apple.com/us/artist/okean-elzy/id362306254"))))
                .contains("362306254");
    }

    @Test
    void emptyWhenNoMatchingLinkOrNullList() {
        assertThat(ExternalIds.deezerArtistId(List.of(link(LinkPlatform.SPOTIFY, "https://open.spotify.com/artist/abc"))))
                .isEmpty();
        assertThat(ExternalIds.deezerArtistId(null)).isEmpty();
        assertThat(ExternalIds.appleArtistId(null)).isEmpty();
        assertThat(ExternalIds.appleArtistId(List.of(link(LinkPlatform.APPLE_MUSIC, "https://music.apple.com/ua/album/x/1"))))
                .isEmpty();
    }
}
