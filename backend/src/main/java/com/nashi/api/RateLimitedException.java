package com.nashi.api;

import org.springframework.http.HttpStatus;

/** 429 {@code RATE_LIMITED}; the response carries {@code Retry-After} with {@link #retryAfterSeconds()} (at least 1). */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super("Забагато запитів. Спробуйте ще раз за " + Math.max(1, retryAfterSeconds) + " с");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }

    @Override
    public String code() {
        return "RATE_LIMITED";
    }
}
