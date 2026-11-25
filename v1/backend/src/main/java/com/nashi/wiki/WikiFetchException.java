package com.nashi.wiki;

import com.nashi.api.exception.ExternalServiceException;

import java.net.http.HttpTimeoutException;
import java.util.OptionalInt;

/** A Wikimedia request failed. Carries the HTTP status when the server answered with one. */
public class WikiFetchException extends ExternalServiceException {

    private final Integer httpStatus;

    public WikiFetchException(String message) {
        super("Wikimedia", message);
        this.httpStatus = null;
    }

    public WikiFetchException(String message, Throwable cause) {
        super("Wikimedia", message, cause);
        this.httpStatus = null;
    }

    /** @param httpStatus the status the server answered with (after any retries) */
    public WikiFetchException(String message, int httpStatus) {
        super("Wikimedia", message);
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
