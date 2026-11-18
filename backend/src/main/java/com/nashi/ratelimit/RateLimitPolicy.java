package com.nashi.ratelimit;

import java.time.Duration;

/** How many requests one client may make per window. */
public enum RateLimitPolicy {
    LIKES(20, Duration.ofSeconds(10)),
    SUBMISSIONS(5, Duration.ofHours(1));

    private final int capacity;
    private final Duration window;

    RateLimitPolicy(int capacity, Duration window) {
        this.capacity = capacity;
        this.window = window;
    }

    public int capacity() {
        return capacity;
    }

    public Duration window() {
        return window;
    }
}
