package com.nashi.artist;

public record AudioClip(String clipId, AudioSource source, String externalId, String key, String url, String title,
                        int durationSec, String attribution, String externalUrl) {
}
