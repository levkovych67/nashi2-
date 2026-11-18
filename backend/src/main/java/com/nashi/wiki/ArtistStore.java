package com.nashi.wiki;

import com.nashi.artist.Artist;

/** The import's view of the artist collection. */
public interface ArtistStore {

    KnownArtists loadKnown();

    boolean slugExists(String slug);

    /** Artists imported from Wikipedia ({@code source = WIKIPEDIA}). */
    long countWikipediaArtists();

    Artist save(Artist artist);
}
