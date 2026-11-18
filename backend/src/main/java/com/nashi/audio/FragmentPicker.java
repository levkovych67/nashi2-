package com.nashi.audio;

import com.nashi.artist.AudioClip;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Chooses a clip and an in-bounds slice of it. Pure: all randomness comes from the given generator. */
public final class FragmentPicker {

    private FragmentPicker() {
    }

    /**
     * @param clips          clips with {@code durationSec > 0}
     * @param excludeClipId  not repeated unless it is the only clip
     */
    public static Optional<Pick> pick(List<AudioClip> clips, @Nullable String excludeClipId, int fragmentSeconds,
                                      RandomGenerator rnd) {
        if (clips.isEmpty()) return Optional.empty();
        List<AudioClip> pool = clips.stream().filter(c -> !c.clipId().equals(excludeClipId)).toList();
        if (pool.isEmpty()) pool = clips;                               // єдиний кліп повторюється
        AudioClip c = pool.get(rnd.nextInt(pool.size()));
        int dur = Math.min(fragmentSeconds, c.durationSec());
        double maxStart = Math.max(0, c.durationSec() - dur);
        double start = Math.floor(rnd.nextDouble() * maxStart * 2) / 2; // крок 0,5 с
        return Optional.of(new Pick(c, start, dur));
    }
}
