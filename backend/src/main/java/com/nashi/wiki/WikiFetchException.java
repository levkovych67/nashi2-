package com.nashi.wiki;

import java.net.http.HttpTimeoutException;
import java.util.OptionalInt;

/**
 * A Wikimedia request failed. Carries the HTTP status when the server answered with one. Internal: it never reaches
 * an API client as it is (the message holds the URL).
 */
public class WikiFetchException extends RuntimeException {

    private final Integer httpStatus;

    public WikiFetchException(String message) {
        super(message);
        this.httpStatus = null;
    }

    public WikiFetchException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
    }

    /** @param httpStatus the status the server answered with (after any retries) */
    public WikiFetchException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public OptionalInt httpStatus() {
        return httpStatus == null ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }

    /** No answer within the client's request timeout. */
    public boolean timedOut() {
        return getCause() instanceof HttpTimeoutException;
    }
}
