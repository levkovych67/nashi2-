package com.nashi.wiki;

/** Makes a fresh source for every run, so caches of places and genres never outlive a run. */
@FunctionalInterface
public interface CandidateSourceFactory {
    ArtistCandidateSource create();
}
