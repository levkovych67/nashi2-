package com.nashi.wiki;

import java.util.List;
import java.util.function.Function;

/**
 * Lists people with any occupation of a tree, a chunk of occupations per query. A chunk that looks too heavy for the
 * endpoint is halved down to a single occupation, and an outage stops the run; see {@link ChunkSplitter}.
 *
 * <p>The start sizes keep the heaviest chunks of the 2026-11-07 research well under the 60 s limit (visual: citizens
 * passed at 400, the born at 100-200; writers: 75 and 37). Musicians at 150 and 60 had 2 chunks in 20 run past the
 * 70 s client timeout, so they start smaller.
 */
public class CandidateLister {

    static final int MUSIC_CITIZEN_CHUNK = 100;
    static final int MUSIC_BORN_CHUNK = 40;
    static final int VISUAL_CITIZEN_CHUNK = 150;
    static final int VISUAL_BORN_CHUNK = 60;
    static final int WRITER_CITIZEN_CHUNK = 40;
    static final int WRITER_BORN_CHUNK = 20;

    private final Function<String, String> sparqlGet;
    private final ChunkSplitter splitter = new ChunkSplitter();

    /** @param sparqlGet runs a SPARQL query and returns the JSON answer */
    public CandidateLister(Function<String, String> sparqlGet) {
        this.sparqlGet = sparqlGet;
    }

    public List<WikiCandidate> list(CandidateGroup group, List<String> occupations, String rule, int chunkSize) {
        return splitter.fetchAll(group + " occupations", occupations, chunkSize, chunk -> SparqlParser.parseCandidates(
                sparqlGet.apply(WikiQueries.peopleWithOccupations(chunk, rule)), group));
    }

    /** Occupations skipped because even a query for that one occupation failed. */
    public int failedChunks() {
        return splitter.skippedItems();
    }
}
