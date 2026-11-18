package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.wiki.WikiHttp;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsAudioProviderTest {

    private final List<String> urls = new ArrayList<>();
    private final WikiHttp http = url -> {
        urls.add(url);
        return url.contains("wikidata.org") ? resource("wikidata-p51.json") : resource("commons-audio-imageinfo.json");
    };
    private final CommonsAudioProvider provider = new CommonsAudioProvider(http);

    private static String resource(String name) {
        try (var in = CommonsAudioProviderTest.class.getResourceAsStream("/audio/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Artist artist() {
        return Artist.builder().id("a1").wikidataId("Q5461945").build();
    }

    @Test
    void commonsKeepsOnlyMp3Playable() {
        List<AudioClip> clips = provider.find(artist());

        // the fixture has two MP3 originals, one .ogg with an MP3 derivative and one .ogg without
        assertThat(clips).extracting(AudioClip::title).containsExactlyInAnyOrder(
                "Hold your hand out, naughty boy", "'Tis a faded picture by Florrie Forde", "Choir with mp3");
        assertThat(clips).allSatisfy(c -> {
            assertThat(c.source()).isEqualTo(AudioSource.COMMONS);
            assertThat(c.url()).endsWith(".mp3").doesNotContain("?");
            assertThat(c.key()).isNull();
        });
    }

    @Test
    void commonsUsesMp3DerivativeOfAnOgg() {
        AudioClip choir = provider.find(artist()).stream().filter(c -> c.title().equals("Choir with mp3")).findFirst()
                .orElseThrow();

        assertThat(choir.url()).isEqualTo(
                "https://upload.wikimedia.org/wikipedia/commons/transcoded/a/aa/Choir_with_mp3.ogg/Choir_with_mp3.ogg.mp3");
        assertThat(choir.durationSec()).isEqualTo(96);
    }

    @Test
    void commonsAttributionHasAuthorAndLicense() {
        List<AudioClip> clips = provider.find(artist());
        AudioClip hold = clips.stream().filter(c -> c.title().startsWith("Hold")).findFirst().orElseThrow();
        AudioClip choir = clips.stream().filter(c -> c.title().equals("Choir with mp3")).findFirst().orElseThrow();

        assertThat(hold.attribution()).isEqualTo("Запис: Florrie Forde, Public domain, Wikimedia Commons");
        // HTML in the author field is reduced to its text
        assertThat(choir.attribution()).isEqualTo("Запис: Example Choir, CC BY-SA 4.0, Wikimedia Commons");
    }

    @Test
    void commonsClipFieldsFollowTheFile() throws Exception {
        AudioClip hold = provider.find(artist()).stream().filter(c -> c.title().startsWith("Hold")).findFirst()
                .orElseThrow();

        String sha1 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1")
                .digest("Hold your hand out, naughty boy.mp3".getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        assertThat(hold.clipId()).isEqualTo("cm_" + sha1);
        assertThat(hold.externalId()).isEqualTo("Hold your hand out, naughty boy.mp3");
        assertThat(hold.durationSec()).isEqualTo(248);
        assertThat(hold.externalUrl()).isEqualTo("https://commons.wikimedia.org/wiki/File:Hold_your_hand_out,_naughty_boy.mp3");
        assertThat(hold.url()).isEqualTo("https://upload.wikimedia.org/wikipedia/commons/c/c3/Hold_your_hand_out%2C_naughty_boy.mp3");
    }

    @Test
    void noWikidataIdMeansNoRequest() {
        assertThat(provider.find(Artist.builder().id("a").build())).isEmpty();
        assertThat(urls).isEmpty();
    }

    @Test
    void noP51StatementsMeansNoCommonsRequest() {
        CommonsAudioProvider empty = new CommonsAudioProvider(url -> "{\"claims\":{}}");

        assertThat(empty.find(artist())).isEmpty();
    }

    @Test
    void asksWikidataForP51ThenCommonsForTheFiles() {
        provider.find(artist());

        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).contains("wikidata.org").contains("property=P51").contains("entity=Q5461945");
        assertThat(urls.get(1)).contains("commons.wikimedia.org").contains("imageinfo").contains("videoinfo")
                .contains("File%3AHold+your+hand+out%2C+naughty+boy.mp3");
    }
}
