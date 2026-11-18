package com.nashi.audio;

import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class FragmentPickerTest {

    private static AudioClip clip(String id, int duration) {
        return new AudioClip(id, AudioSource.UPLOAD, null, "k", null, "T", duration, "a", null);
    }

    private static RandomGenerator seeded(long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        return new RandomGenerator() {
            @Override public long nextLong() { return r.nextLong(); }
            @Override public int nextInt(int bound) { return r.nextInt(bound); }
            @Override public double nextDouble() { return r.nextDouble(); }
        };
    }

    @Test
    void emptyClipsGiveEmpty() {
        assertThat(FragmentPicker.pick(List.of(), null, 15, seeded(1))).isEmpty();
    }

    @Test
    void excludesGivenClipWhenOthersExist() {
        List<AudioClip> clips = List.of(clip("c1", 30), clip("c2", 30), clip("c3", 30));
        for (long seed = 0; seed < 1000; seed++) {
            Optional<Pick> pick = FragmentPicker.pick(clips, "c2", 15, seeded(seed));
            assertThat(pick.orElseThrow().clip().clipId()).isNotEqualTo("c2");
        }
    }

    @Test
    void singleClipRepeatsEvenIfExcluded() {
        Pick pick = FragmentPicker.pick(List.of(clip("c1", 30)), "c1", 15, seeded(1)).orElseThrow();
        assertThat(pick.clip().clipId()).isEqualTo("c1");
    }

    @Test
    void fragmentFitsInsideClip() {
        List<AudioClip> clips = List.of(clip("a", 9), clip("b", 15), clip("c", 30), clip("d", 47));
        for (long seed = 0; seed < 10_000; seed++) {
            Pick p = FragmentPicker.pick(clips, null, 15, seeded(seed)).orElseThrow();
            assertThat(p.startSec()).isGreaterThanOrEqualTo(0);
            assertThat(p.startSec() + p.durationSec()).isLessThanOrEqualTo(p.clip().durationSec());
        }
    }

    @Test
    void shortClipPlaysWholeFromZero() {
        for (long seed = 0; seed < 100; seed++) {
            Pick p = FragmentPicker.pick(List.of(clip("s", 9)), null, 15, seeded(seed)).orElseThrow();
            assertThat(p.startSec()).isZero();
            assertThat(p.durationSec()).isEqualTo(9);
        }
    }

    @Test
    void previewClip30sGivesStartUpToFifteenInHalfSeconds() {
        double max = -1;
        for (long seed = 0; seed < 10_000; seed++) {
            Pick p = FragmentPicker.pick(List.of(clip("p", 30)), null, 15, seeded(seed)).orElseThrow();
            assertThat(p.durationSec()).isEqualTo(15);
            assertThat(p.startSec()).isBetween(0.0, 15.0);
            assertThat(p.startSec() * 2).isEqualTo(Math.floor(p.startSec() * 2));
            max = Math.max(max, p.startSec());
        }
        assertThat(max).isGreaterThan(14.0);
    }
}
