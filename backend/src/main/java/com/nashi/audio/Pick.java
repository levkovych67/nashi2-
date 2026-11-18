package com.nashi.audio;

import com.nashi.artist.AudioClip;

/** A chosen clip with the slice to play: {@code startSec} in 0.5 s steps, {@code durationSec} whole seconds. */
public record Pick(AudioClip clip, double startSec, int durationSec) {
}
