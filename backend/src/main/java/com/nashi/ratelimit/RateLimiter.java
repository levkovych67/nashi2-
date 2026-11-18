package com.nashi.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nashi.api.RateLimitedException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-key token buckets, one Caffeine cache per policy so LIKES and SUBMISSIONS never share a bucket. A bucket
 * idle for a whole window is full again, so {@code expireAfterAccess(window)} drops it without changing behavior.
 * Time comes from the injected {@link Clock} so tests can advance it.
 */
@Component
public class RateLimiter {

    static final long MAX_KEYS = 100_000;

    private final Map<RateLimitPolicy, Cache<String, Bucket>> buckets = new EnumMap<>(RateLimitPolicy.class);
    private final TimeMeter timeMeter;

    public RateLimiter(Clock clock) {
        this.timeMeter = new ClockTimeMeter(clock);
        for (RateLimitPolicy p : RateLimitPolicy.values()) {
            buckets.put(p, Caffeine.newBuilder().maximumSize(MAX_KEYS).expireAfterAccess(p.window()).build());
        }
    }

    /** Takes one token for {@code key} or throws {@link RateLimitedException} with the seconds until one is back. */
    public void check(RateLimitPolicy policy, String key) {
        Bucket bucket = buckets.get(policy).get(key, k -> newBucket(policy));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;
            throw new RateLimitedException(Math.max(1, Math.min(seconds, policy.window().toSeconds())));
        }
    }

    private Bucket newBucket(RateLimitPolicy policy) {
        // Interval refill: all tokens return at once, one window after the first spend, so it is a strict
        // "N per window" (greedy would trickle tokens back and let a steady client exceed N within a window).
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(policy.capacity())
                        .refillIntervally(policy.capacity(), policy.window()).build())
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            var i = clock.instant();
            return Math.addExact(Math.multiplyExact(i.getEpochSecond(), 1_000_000_000L), i.getNano());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
