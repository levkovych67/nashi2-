package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CandidateListerTest {

    private static final Pattern VALUES = Pattern.compile("VALUES \\?occ \\{ ([^}]*) }");

    private final List<String> queries = new ArrayList<>();

    /** Answers like the endpoint: one person per occupation in the query, item Q{n}00 for occupation Q{n}. */
    private Function<String, String> endpoint(Predicate<List<String>> fails, String failure) {
        return query -> {
            queries.add(query);
            List<String> occupations = occupationsOf(query);
            if (fails.test(occupations)) {
                if (failure == null) {
                    throw http(500);
                }
                return failure;
            }
            return occupations.stream()
                    .map(occ -> "{\"item\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + occ + "00\"},"
                            + "\"title\":{\"xml:lang\":\"uk\",\"type\":\"literal\",\"value\":\"Особа " + occ + "\"}}")
                    .collect(Collectors.joining(",",
                            "{\"head\":{\"vars\":[\"item\",\"title\"]},\"results\":{\"bindings\":[", "]}}"));
        };
    }

    /** As PoliteWikiHttp reports a status it gave up on (after its retries, for 429 and 5xx). */
    private static WikiFetchException http(int status) {
        return new WikiFetchException("HTTP " + status + " for https://query.wikidata.org/sparql…", status);
    }

    /** As PoliteWikiHttp reports the client's request timeout (after its retries). */
    private static WikiFetchException timeout() {
        return new WikiFetchException("request failed: https://query.wikidata.org/sparql…",
                new HttpTimeoutException("request timed out"));
    }

    /** Like {@link #endpoint}, but a failing chunk throws the given exception. */
    private Function<String, String> endpointThatThrows(Predicate<List<String>> fails, RuntimeException failure) {
        Function<String, String> answers = endpoint(occs -> false, null);
        return query -> {
            if (fails.test(occupationsOf(query))) {
                queries.add(query);
                throw failure;
            }
            return answers.apply(query);
        };
    }

    /** A lister whose endpoint always fails with the given exception. */
    private CandidateLister failingWith(RuntimeException failure) {
        return new CandidateLister(query -> {
            queries.add(query);
            throw failure;
        });
    }

    private static List<String> occupationsOf(String query) {
        Matcher matcher = VALUES.matcher(query);
        assertTrue(matcher.find(), query);
        return List.of(matcher.group(1).replace("wd:", "").trim().split(" "));
    }

    private static List<String> occupations(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ids.add("Q" + i);
        }
        return ids;
    }

    private static List<String> qids(List<WikiCandidate> candidates) {
        return candidates.stream().map(WikiCandidate::qid).toList();
    }

    @Test
    void splitsFailingChunkInHalves() {
        CandidateLister lister = new CandidateLister(endpoint(occs -> occs.size() == 8, null));

        List<WikiCandidate> found = lister.list(CandidateGroup.MUSIC, occupations(8), WikiQueries.CITIZEN, 8);

        assertEquals(List.of("Q100", "Q200", "Q300", "Q400", "Q500", "Q600", "Q700", "Q800"), qids(found));
        assertEquals(3, queries.size());
        assertEquals(List.of(8, 4, 4), queries.stream().map(q -> occupationsOf(q).size()).toList());
        assertEquals(0, lister.failedChunks());
    }

    @Test
    void treatsUnparseableJsonAsAFailedChunk() {
        // The endpoint cuts the body off when it times out mid-answer.
        String truncated = "{\"head\":{\"vars\":[\"item\",\"title\"]},\"results\":{\"bindings\":[{\"item\":";
        CandidateLister lister = new CandidateLister(endpoint(occs -> occs.size() == 4, truncated));

        List<WikiCandidate> found = lister.list(CandidateGroup.ENSEMBLE, occupations(4), WikiQueries.BORN, 4);

        assertEquals(List.of("Q100", "Q200", "Q300", "Q400"), qids(found));
        assertEquals(3, queries.size());
        assertEquals(0, lister.failedChunks());
    }

    @Test
    void skipsSingleFailingOccupationAndCountsIt() {
        CandidateLister lister = new CandidateLister(endpoint(occs -> occs.contains("Q3"), null));

        List<WikiCandidate> found = lister.list(CandidateGroup.ENSEMBLE, occupations(4), WikiQueries.CITIZEN, 4);

        assertEquals(List.of("Q100", "Q200", "Q400"), qids(found));
        assertEquals(List.of(4, 2, 2, 1, 1), queries.stream().map(q -> occupationsOf(q).size()).toList());
        assertEquals(1, lister.failedChunks());
    }

    @Test
    void countsEveryOccupationThatFailsAlone() {
        CandidateLister lister = new CandidateLister(endpoint(occs -> occs.contains("Q1") || occs.contains("Q5"), null));

        List<WikiCandidate> found = lister.list(CandidateGroup.MUSIC, occupations(6), WikiQueries.BORN, 3);

        assertEquals(List.of("Q200", "Q300", "Q400", "Q600"), qids(found));
        assertEquals(2, lister.failedChunks());
    }

    @Test
    void tagsCandidatesWithGroup() {
        CandidateLister lister = new CandidateLister(endpoint(occs -> false, null));

        List<WikiCandidate> found = lister.list(CandidateGroup.ENSEMBLE, List.of("Q49757", "Q36180"), WikiQueries.BORN, 40);

        assertEquals(List.of(
                new WikiCandidate("Q4975700", "Особа Q49757", CandidateGroup.ENSEMBLE),
                new WikiCandidate("Q3618000", "Особа Q36180", CandidateGroup.ENSEMBLE)), found);
        assertEquals(List.of(WikiQueries.peopleWithOccupations(List.of("Q49757", "Q36180"), WikiQueries.BORN)), queries);
    }

    @Test
    void asksInChunksOfTheGivenSize() {
        CandidateLister lister = new CandidateLister(endpoint(occs -> false, null));

        List<WikiCandidate> found = lister.list(CandidateGroup.MUSIC, occupations(5), WikiQueries.CITIZEN, 2);

        assertEquals(5, found.size());
        assertEquals(List.of(List.of("Q1", "Q2"), List.of("Q3", "Q4"), List.of("Q5")),
                queries.stream().map(CandidateListerTest::occupationsOf).toList());
    }

    @Test
    void splitsAChunkThatTimedOut() {
        CandidateLister lister = new CandidateLister(endpointThatThrows(occs -> occs.size() == 4, timeout()));

        List<WikiCandidate> found = lister.list(CandidateGroup.MUSIC, occupations(4), WikiQueries.CITIZEN, 4);

        assertEquals(4, found.size());
        assertEquals(List.of(4, 2, 2), queries.stream().map(q -> occupationsOf(q).size()).toList());
    }

    @Test
    void alwaysFailingEndpointStopsAfterTwelveCalls() {
        CandidateLister lister = failingWith(timeout());

        WikiFetchException e = assertThrows(WikiFetchException.class,
                () -> lister.list(CandidateGroup.MUSIC, occupations(150), WikiQueries.CITIZEN, 150));

        assertTrue(e.getMessage().contains("Wikidata недоступна: 12 запитів поспіль не вдалися"), e.getMessage());
        assertEquals(12, queries.size(), "not 2 × 150 − 1 = 299");
    }

    @Test
    void oneBadOccupationInAFullChunkDoesNotTripTheBreaker() {
        // Splitting 150 down to Q1 alone fails 8 times in a row: 150, 75, 37, 18, 9, 4, 2, 1.
        CandidateLister lister = new CandidateLister(endpoint(occs -> occs.contains("Q1"), null));

        List<WikiCandidate> found = lister.list(CandidateGroup.MUSIC, occupations(150), WikiQueries.CITIZEN, 150);

        assertEquals(149, found.size());
        assertFalse(qids(found).contains("Q100"));
        assertEquals(1, lister.failedChunks());
    }

    @Test
    void http429IsNotSplit() {
        WikiFetchException limited = http(429);
        CandidateLister lister = failingWith(limited);

        assertSame(limited, assertThrows(WikiFetchException.class,
                () -> lister.list(CandidateGroup.MUSIC, occupations(8), WikiQueries.CITIZEN, 8)));
        assertEquals(1, queries.size());
    }

    @Test
    void http403IsNotSplit() {
        WikiFetchException forbidden = http(403);
        CandidateLister lister = failingWith(forbidden);

        assertSame(forbidden, assertThrows(WikiFetchException.class,
                () -> lister.list(CandidateGroup.ENSEMBLE, occupations(8), WikiQueries.BORN, 8)));
        assertEquals(1, queries.size());
    }

    @Test
    void otherClientErrorsAreNotSplit() {
        for (int status : new int[] {400, 404, 414}) {
            queries.clear();
            WikiFetchException failure = http(status);
            CandidateLister lister = failingWith(failure);

            assertSame(failure, assertThrows(WikiFetchException.class,
                    () -> lister.list(CandidateGroup.ENSEMBLE, occupations(8), WikiQueries.BORN, 8)), "HTTP " + status);
            assertEquals(1, queries.size(), "HTTP " + status);
        }
    }

    @Test
    void connectionFailureIsNotSplit() {
        WikiFetchException refused = new WikiFetchException("request failed: https://query.wikidata.org/sparql…",
                new ConnectException("Connection refused"));
        CandidateLister lister = failingWith(refused);

        assertSame(refused, assertThrows(WikiFetchException.class,
                () -> lister.list(CandidateGroup.MUSIC, occupations(8), WikiQueries.CITIZEN, 8)));
        assertEquals(1, queries.size());
    }

    @Test
    void programmingErrorIsNotSplit() {
        CandidateLister lister = failingWith(new NullPointerException("a bug, not the endpoint"));

        assertThrows(NullPointerException.class,
                () -> lister.list(CandidateGroup.MUSIC, occupations(8), WikiQueries.CITIZEN, 8));
        assertEquals(1, queries.size());
    }

    @Test
    void successResetsTheBreaker() {
        // Q1-Q11 fail, Q12 answers, Q13-Q23 fail: 22 failures, never 12 in a row.
        CandidateLister lister = new CandidateLister(endpoint(occs -> !occs.contains("Q12"), null));

        List<WikiCandidate> found = lister.list(CandidateGroup.MUSIC, occupations(23), WikiQueries.CITIZEN, 1);

        assertEquals(List.of("Q1200"), qids(found));
        assertEquals(22, lister.failedChunks());
        assertEquals(23, queries.size());
    }

    @Test
    void stopsInsteadOfSplittingWhenTheThreadIsInterrupted() {
        CandidateLister lister = new CandidateLister(query -> {
            queries.add(query);
            Thread.currentThread().interrupt();
            throw new WikiFetchException("interrupted: https://query.wikidata.org/sparql…");
        });
        try {
            assertThrows(WikiFetchException.class,
                    () -> lister.list(CandidateGroup.MUSIC, occupations(8), WikiQueries.CITIZEN, 8));
            assertEquals(1, queries.size());
        } finally {
            Thread.interrupted();
        }
    }
}
