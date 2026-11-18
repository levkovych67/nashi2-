package com.nashi.audio;

import com.nashi.artist.AudioSource;

import java.time.Instant;

/** {@code Fragment} of {@code docs/02-contracts.md} 2.7. {@code expiresAt} is set only for temporary (Deezer) URLs. */
public record Fragment(String clipId, String url, double startSec, int durationSec, int fadeInMs, int fadeOutMs,
                       String title, AudioSource source, String attribution, String externalUrl, Instant expiresAt) {
}
