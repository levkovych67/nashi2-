package com.nashi.audio;

import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;

import java.util.List;

/** Finds preview clips for a musician from one external source, by ids the musician already has. */
public interface AudioProvider {

    AudioSource source();

    /** @return at most five clips; empty when the musician has no id for this source. May throw on a network error. */
    List<AudioClip> find(Artist artist);
}
