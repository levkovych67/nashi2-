package com.nashi.audio;

import com.nashi.api.NotFoundException;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.media.FileStorage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FragmentServiceTest {

    private final ArtistRepository artists = mock(ArtistRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final DeezerPreviewUrls deezer = mock(DeezerPreviewUrls.class);

    private static AudioClip clip(String id, AudioSource source, int duration) {
        return new AudioClip(id, source, "ext" + id, "key-" + id, "https://stored/" + id, "T" + id, duration, "attr", null);
    }

    private FragmentService service(AudioPolicy policy, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        RandomGenerator rnd = new RandomGenerator() {
            @Override public long nextLong() { return r.nextLong(); }
            @Override public int nextInt(int bound) { return r.nextInt(bound); }
            @Override public double nextDouble() { return r.nextDouble(); }
        };
        when(storage.publicUrl(anyString())).thenAnswer(i -> "https://cdn/" + i.getArgument(0));
        return new FragmentService(artists, policy, storage, deezer, 15, () -> rnd);
    }

    private void artist(AudioClip... clips) {
        when(artists.findById("a1")).thenReturn(Optional.of(
                Artist.builder().status(ArtistStatus.PUBLISHED).audio(List.of(clips)).build()));
    }

    @Test
    void unpublishedOrUnknownIs404() {
        when(artists.findById("h")).thenReturn(Optional.of(Artist.builder().status(ArtistStatus.HIDDEN).build()));
        FragmentService s = service(AudioPolicy.of(true, true, true), 1);
        assertThatThrownBy(() -> s.fragment("h", null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> s.fragment("zzz", null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void clipsOffPolicyOrWithoutDurationAreNeverPicked() {
        artist(clip("d", AudioSource.DEEZER, 30), clip("z", AudioSource.UPLOAD, 0));
        assertThat(service(AudioPolicy.of(false, false, false), 1).fragment("a1", null)).isEmpty();
    }

    @Test
    void deezerRateLimitFallsBackToOtherClip() {
        artist(clip("d", AudioSource.DEEZER, 30), clip("u", AudioSource.UPLOAD, 30));
        when(deezer.fresh(anyString())).thenReturn(Optional.empty());
        for (long seed = 0; seed < 50; seed++) {
            Fragment f = service(AudioPolicy.of(true, true, true), seed).fragment("a1", null).orElseThrow();
            assertThat(f.clipId()).isEqualTo("u");
            assertThat(f.url()).isEqualTo("https://cdn/key-u");
            assertThat(f.expiresAt()).isNull();
        }
    }

    @Test
    void onlyDeezerClipsAndNoUrlGivesEmpty() {
        artist(clip("d1", AudioSource.DEEZER, 30), clip("d2", AudioSource.DEEZER, 30));
        when(deezer.fresh(anyString())).thenReturn(Optional.empty());
        assertThat(service(AudioPolicy.of(true, true, true), 3).fragment("a1", null)).isEmpty();
    }

    @Test
    void deezerFragmentCarriesFreshUrlAndExpiry() {
        artist(clip("d", AudioSource.DEEZER, 30));
        Instant exp = Instant.parse("2026-11-17T10:09:00Z");
        when(deezer.fresh("extd")).thenReturn(Optional.of(new DeezerPreviewUrls.PreviewUrl("https://dz/x.mp3", exp)));
        Fragment f = service(AudioPolicy.of(true, true, true), 3).fragment("a1", null).orElseThrow();
        assertThat(f.url()).isEqualTo("https://dz/x.mp3");
        assertThat(f.expiresAt()).isEqualTo(exp);
        assertThat(f.fadeInMs()).isEqualTo(300);
        assertThat(f.fadeOutMs()).isEqualTo(600);
        assertThat(f.durationSec()).isEqualTo(15);
    }
}
