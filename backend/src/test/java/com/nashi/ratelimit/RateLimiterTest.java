package com.nashi.ratelimit;

import com.nashi.api.RateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** A clock the test advances by hand. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allows20LikesRequestsPer10Seconds() {
        for (int i = 0; i < 20; i++) {
            limiter.check(RateLimitPolicy.LIKES, "1.1.1.1");
        }
        assertThatThrownBy(() -> limiter.check(RateLimitPolicy.LIKES, "1.1.1.1"))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 10L));
    }

    @Test
    void refillsAfterWindow() {
        for (int i = 0; i < 20; i++) {
            limiter.check(RateLimitPolicy.LIKES, "1.1.1.1");
        }
        clock.advance(Duration.ofSeconds(10));
        for (int i = 0; i < 20; i++) {
            limiter.check(RateLimitPolicy.LIKES, "1.1.1.1");
        }
        assertThatThrownBy(() -> limiter.check(RateLimitPolicy.LIKES, "1.1.1.1"))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 20; i++) {
            limiter.check(RateLimitPolicy.LIKES, "1.1.1.1");
        }
        assertThatCode(() -> limiter.check(RateLimitPolicy.LIKES, "2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void policiesDoNotShareBuckets() {
        for (int i = 0; i < 20; i++) {
            limiter.check(RateLimitPolicy.LIKES, "1.1.1.1");
        }
        assertThatCode(() -> limiter.check(RateLimitPolicy.SUBMISSIONS, "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void submissionsAllow5PerHourAndRetryAfterIsClamped() {
        for (int i = 0; i < 5; i++) {
            limiter.check(RateLimitPolicy.SUBMISSIONS, "1.1.1.1");
        }
        assertThatThrownBy(() -> limiter.check(RateLimitPolicy.SUBMISSIONS, "1.1.1.1"))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 3600L));
    }
}
