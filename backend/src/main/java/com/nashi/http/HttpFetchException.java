package com.nashi.http;

/** An outgoing HTTP call failed for good: a non-retryable status, retries used up, or a transport error. */
public class HttpFetchException extends RuntimeException {

    private final int status;

    public HttpFetchException(String message, int status) {
        super(message);
        this.status = status;
    }

    public HttpFetchException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** The HTTP status that ended the call; -1 when there was none (timeout, connection error, bad body). */
    public int status() {
        return status;
    }
}
