package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItunesAudioProviderTest {

    private final HttpJson http = mock(HttpJson.class);
    private final ItunesAudioProvider provider = new ItunesAudioProvider(http);

    private static Artist artistWithApple() {
        return Artist.builder().id("a1").links(List.of(
                new Link(LinkPlatform.APPLE_MUSIC, "https://music.apple.com/ua/artist/okean-elzy/362306254"))).build();
    }

    @Test
    void itunesMapsOnlyTracksWithPreview() {
        when(http.get("https://itunes.apple.com/lookup?id=362306254&entity=song&limit=5&country=UA"))
                .thenReturn(DeezerAudioProviderTest.fixture("itunes-lookup.json"));

        List<AudioClip> clips = provider.find(artistWithApple());

        assertThat(clips).hasSize(3);
        assertThat(clips).extracting(AudioClip::externalId).doesNotContain("999000002");
        AudioClip first = clips.get(0);
        assertThat(first.clipId()).isEqualTo("it_1033595614");
        assertThat(first.source()).isEqualTo(AudioSource.ITUNES);
        assertThat(first.externalId()).isEqualTo("1033595614");
        assertThat(first.key()).isNull();
        assertThat(first.url()).startsWith("https://audio-ssl.itunes.apple.com/").endsWith(".m4a");
        assertThat(first.title()).isEqualTo("Обійми");
        assertThat(first.durationSec()).isEqualTo(30);
        assertThat(first.attribution()).isEqualTo("Прев'ю: Apple Music");
        assertThat(first.externalUrl()).startsWith("https://music.apple.com/ua/album/");
    }

    @Test
    void noAppleLinkMeansNoRequest() {
        assertThat(provider.find(Artist.builder().id("a").build())).isEmpty();
        verify(http, never()).get(anyString());
    }
}
