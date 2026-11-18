package com.nashi.wiki;

/** Waits between requests; replaced in tests so they do not wait. */
public interface Sleeper {

    void sleep(long millis);

    Sleeper REAL = millis -> {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WikiFetchException("interrupted while waiting", e);
        }
    };
}
