package com.nashi.wiki;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Wikidata item that has an article in the Ukrainian Wikipedia.
 *
 * @param group the candidate query that found it first
 */
public record WikiCandidate(String qid, String ukTitle, CandidateGroup group) {

    /** The first {@code limit} candidates of each group, in their order; all of them when {@code limit <= 0}. */
    public static List<WikiCandidate> limitPerGroup(List<WikiCandidate> candidates, int limit) {
        if (limit <= 0) {
            return candidates;
        }
        Map<CandidateGroup, Integer> taken = new HashMap<>();
        return candidates.stream()
                .filter(candidate -> taken.merge(candidate.group(), 1, Integer::sum) <= limit)
                .toList();
    }
}
