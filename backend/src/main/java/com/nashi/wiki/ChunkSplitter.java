package com.nashi.wiki;

import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Asks for a list of items a chunk at a time (occupations of a tree, creators of works). A chunk that looks too heavy
 * for the endpoint (the answer was cut off, HTTP 500/502/503/504, or the client timed out) is split in two and each
 * half is asked again, down to a single item; one that still fails is skipped and counted. Anything else (429 or 403
 * after the retries, other 4xx, a connection failure, a bug) is rethrown at once, and so is the {@value
 * #MAX_FAILURES_IN_A_ROW}th failed request in a row: an outage must stop the run, not multiply requests. The counts
 * carry over between calls on one instance.
 */
@Slf4j
final class ChunkSplitter {

    /**
     * Above the depth of one split descent: halving 150 occupations down to a single one fails 8 times in a row
     * (150, 75, 37, 18, 9, 4, 2, 1), so one bad occupation must not stop the run.
     */
    static final int MAX_FAILURES_IN_A_ROW = 12;

    private int skippedItems;
    private int failuresInARow;

    /**
     * @param what  names the items in log lines, e.g. "MUSIC occupations"
     * @param fetch asks for one chunk and parses the answer; a cut-off answer must make it throw
     * @return what every chunk answered, in chunk order
     */
    <T, R> List<R> fetchAll(String what, List<T> items, int chunkSize, Function<List<T>, List<R>> fetch) {
        List<R> found = new ArrayList<>();
        for (List<T> chunk : WikiCollector.batches(items, chunkSize)) {
            fetch(what, chunk, fetch, found);
        }
        return found;
    }

    /** Items left out because even a request for that one item failed. */
    int skippedItems() {
        return skippedItems;
    }

    private <T, R> void fetch(String what, List<T> chunk, Function<List<T>, List<R>> fetch, List<R> found) {
        try {
            List<R> answer = fetch.apply(chunk);
            failuresInARow = 0;
            log.info("[WIKI] {}: chunk of {} from {}: {} found", what, chunk.size(), chunk.get(0), answer.size());
            found.addAll(answer);
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted() || !tooHeavy(e)) {
                throw e;
            }
            failuresInARow++;
            if (failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                throw new WikiFetchException("Wikidata недоступна: " + failuresInARow
                        + " запитів поспіль не вдалися", e);
            }
            if (chunk.size() == 1) {
                skippedItems++;
                log.warn("[WIKI] {}: {} skipped, its query failed: {}", what, chunk.get(0), firstLine(e));
                return;
            }
            log.info("[WIKI] {}: chunk of {} from {} failed ({}), splitting it", what, chunk.size(), chunk.get(0),
                    firstLine(e));
            int middle = chunk.size() / 2;
            fetch(what, chunk.subList(0, middle), fetch, found);
            fetch(what, chunk.subList(middle, chunk.size()), fetch, found);
        }
    }

    /** Whether halving the chunk may help: the endpoint ran out of time on it, rather than refusing or being down. */
    static boolean tooHeavy(RuntimeException e) {
        if (e instanceof JsonParseException) {
            return true;
        }
        if (e instanceof WikiFetchException fetch) {
            int status = fetch.httpStatus().orElse(0);
            return fetch.timedOut() || status == 500 || status == 502 || status == 503 || status == 504;
        }
        return false;
    }

    private static String firstLine(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
