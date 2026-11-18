package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPolicyTest {

    static AudioClip clip(AudioSource source) {
        return new AudioClip("c_" + source, source, "x", null, null, "T", 30, "a", null);
    }

    @Test
    void uploadsAreAlwaysPlayableAndOtherSourcesFollowTheSwitches() {
        AudioPolicy policy = AudioPolicy.of(false, true, false);

        assertThat(policy.playable(clip(AudioSource.UPLOAD))).isTrue();
        assertThat(policy.playable(clip(AudioSource.DEEZER))).isFalse();
        assertThat(policy.playable(clip(AudioSource.ITUNES))).isTrue();
        assertThat(policy.playable(clip(AudioSource.COMMONS))).isFalse();
    }

    @Test
    void hasAudioCountsOnlyPlayableClips() {
        AudioPolicy policy = AudioPolicy.of(false, false, true);

        assertThat(policy.hasAudio(Artist.builder().audio(List.of(clip(AudioSource.DEEZER))).build())).isFalse();
        assertThat(policy.hasAudio(Artist.builder().audio(List.of(clip(AudioSource.DEEZER), clip(AudioSource.COMMONS)))
                .build())).isTrue();
        assertThat(policy.hasAudio(Artist.builder().audio(List.of(clip(AudioSource.UPLOAD))).build())).isTrue();
        assertThat(policy.hasAudio(Artist.builder().audio(List.of()).build())).isFalse();
        assertThat(policy.hasAudio(Artist.builder().build())).isFalse();
    }

    @Test
    void playableClipsFiltersAList() {
        AudioPolicy policy = AudioPolicy.of(false, true, true);

        assertThat(policy.playableClips(List.of(clip(AudioSource.DEEZER), clip(AudioSource.ITUNES))))
                .extracting(AudioClip::source).containsExactly(AudioSource.ITUNES);
        assertThat(policy.playableClips(null)).isEmpty();
    }

    @Test
    void enabledReportsProviderSwitches() {
        AudioPolicy policy = AudioPolicy.of(true, false, true);

        assertThat(policy.enabled(AudioSource.DEEZER)).isTrue();
        assertThat(policy.enabled(AudioSource.ITUNES)).isFalse();
        assertThat(policy.enabled(AudioSource.UPLOAD)).isTrue();
    }
}
