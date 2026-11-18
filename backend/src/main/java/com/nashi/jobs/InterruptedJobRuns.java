package com.nashi.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * At startup, right after the indexes, marks the {@code jobs_runs} entries still {@code RUNNING} as {@code FAILED}
 * with {@code {error: "Interrupted"}}: the deployment is a single instance, so a run left {@code RUNNING} by the
 * previous process was cut off by the restart and would otherwise stay "running" forever. Only runs started before
 * this bean was created count, so a job started right after the bot connected (polling starts before the runners)
 * is not touched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InterruptedJobRuns implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InterruptedJobRuns.class);

    private final JobRunLog runs;
    private final Instant bootedAt;

    public InterruptedJobRuns(JobRunLog runs, Clock clock) {
        this.runs = runs;
        this.bootedAt = clock.instant();
    }

    @Override
    public void run(ApplicationArguments args) {
        long failed = runs.failInterrupted(bootedAt);
        if (failed > 0) {
            log.warn("Marked {} job run(s) left RUNNING by the previous process as FAILED (Interrupted)", failed);
        }
    }
}
