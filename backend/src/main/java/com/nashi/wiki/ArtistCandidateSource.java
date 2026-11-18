package com.nashi.wiki;

import java.util.List;

/** Where the import gets its artists from. */
public interface ArtistCandidateSource {

    /** Every item that may be imported, each once. */
    List<WikiCandidate> listCandidates();

    /** Candidate queries that failed for good in the last {@link #listCandidates()}; their items are missing. */
    default int failedCandidateChunks() {
        return 0;
    }

    /**
     * Items left out by {@link #fetchDetails} so far because they are linked to Russia: Russian citizenship or a state
     * award or title of Russia.
     */
    default int russiaLinkedExcluded() {
        return 0;
    }

    /** Full data for the given candidates; items that must not be imported are left out. */
    List<WikiArtistData> fetchDetails(List<WikiCandidate> candidates);
}
