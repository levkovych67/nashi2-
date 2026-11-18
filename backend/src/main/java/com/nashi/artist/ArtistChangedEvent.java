package com.nashi.artist;

/**
 * Published after an artist is created or its status, origin, pin, name or photo changes (not after likes), so the
 * map snapshot can be rebuilt.
 */
public record ArtistChangedEvent(String artistId) {
}
