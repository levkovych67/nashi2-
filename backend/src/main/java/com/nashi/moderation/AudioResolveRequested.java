package com.nashi.moderation;

/** Published after an approved submission became an artist; T20 looks for playable audio. */
public record AudioResolveRequested(String artistId) {
}
