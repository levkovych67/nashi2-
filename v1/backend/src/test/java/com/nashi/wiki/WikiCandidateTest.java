package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikiCandidateTest {

    private final List<WikiCandidate> candidates = List.of(
            new WikiCandidate("Q1", "1", CandidateGroup.MUSIC),
            new WikiCandidate("Q2", "2", CandidateGroup.ENSEMBLE),
            new WikiCandidate("Q3", "3", CandidateGroup.MUSIC),
            new WikiCandidate("Q4", "4", CandidateGroup.MUSIC),
            new WikiCandidate("Q5", "5", CandidateGroup.WRITER));

    @Test
    void keepsTheFirstNOfEachGroupInOrder() {
        assertEquals(List.of("Q1", "Q2", "Q3", "Q5"),
                WikiCandidate.limitPerGroup(candidates, 2).stream().map(WikiCandidate::qid).toList());
    }

    @Test
    void zeroOrLessKeepsAll() {
        assertEquals(candidates, WikiCandidate.limitPerGroup(candidates, 0));
        assertEquals(candidates, WikiCandidate.limitPerGroup(candidates, -1));
    }
}
