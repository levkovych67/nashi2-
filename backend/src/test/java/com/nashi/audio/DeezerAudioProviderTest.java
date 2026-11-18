package com.nashi.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeezerAudioProviderTest {

    static JsonObject fixture(String name) {
        try (var in = DeezerAudioProviderTest.class.getResourceAsStream("/audio/" + name)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private final HttpJson http = mock(HttpJson.class);
    private final DeezerAudioProvider provider = new DeezerAudioProvider(http);

    private static Artist artistWithDeezer() {
        return Artist.builder().id("a1").links(List.of(
                new Link(LinkPlatform.DEEZER, "https://www.deezer.com/en/artist/13612387"))).build();
    }

    @Test
    void deezerMapsTopTracksWithoutStoringUrl() {
        when(http.get("https://api.deezer.com/artist/13612387/top?limit=5")).thenReturn(fixture("deezer-artist-top.json"));

        List<AudioClip> clips = provider.find(artistWithDeezer());

        assertThat(clips).hasSize(3);
        AudioClip first = clips.get(0);
        assertThat(first.clipId()).isEqualTo("dz_437046332");
        assertThat(first.source()).isEqualTo(AudioSource.DEEZER);
        assertThat(first.externalId()).isEqualTo("437046332");
        assertThat(first.url()).isNull();
        assertThat(first.key()).isNull();
        assertThat(first.title()).isEqualTo("Beggin'");
        assertThat(first.durationSec()).isEqualTo(30);
        assertThat(first.attribution()).isEqualTo("Прев'ю: Deezer");
        assertThat(first.externalUrl()).isEqualTo("https://www.deezer.com/track/437046332");
        assertThat(clips).allSatisfy(c -> assertThat(c.url()).isNull());
    }

    @Test
    void deezerSkipsTracksWithoutPreview() {
        when(http.get("https://api.deezer.com/artist/13612387/top?limit=5")).thenReturn(fixture("deezer-artist-top.json"));

        assertThat(provider.find(artistWithDeezer())).extracting(AudioClip::externalId).doesNotContain("999000001");
    }

    @Test
    void noDeezerLinkMeansNoRequest() {
        assertThat(provider.find(Artist.builder().id("a").build())).isEmpty();
        verify(http, never()).get(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sourceIsDeezer() {
        assertThat(provider.source()).isEqualTo(AudioSource.DEEZER);
    }
}
