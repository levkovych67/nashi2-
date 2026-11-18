package com.nashi.jobs;

import com.nashi.telegram.Html;
import com.nashi.telegram.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The {@code jobs_runs} log. Every background job (cleanup, wiki import, audio resolve, V1 migration) should go
 * through {@link #run}: it records the run, and when the job throws it records {@code FAILED}, tells the moderation
 * chat "⚠️ Задача {kind} завершилася з помилкою: {exception class name}" (never the message, which may hold secrets)
 * and rethrows. {@link #start} / {@link #finish} are the lower-level pieces.
 */
@Component
public class JobRunLog {

    public static final String RUNNING = "RUNNING";
    public static final String OK = "OK";
    public static final String FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(JobRunLog.class);

    private final MongoTemplate mongo;
    private final Clock clock;
    private final TelegramGateway gateway;

    public JobRunLog(MongoTemplate mongo, Clock clock, TelegramGateway gateway) {
        this.mongo = mongo;
        this.clock = clock;
        this.gateway = gateway;
    }

    /** Inserts a {@code RUNNING} run and returns its id. */
    public String start(JobKind kind) {
        JobRun run = new JobRun();
        run.setJob(kind);
        run.setStartedAt(clock.instant());
        run.setStatus(RUNNING);
        return mongo.insert(run).getId();
    }

    public void finish(String runId, String status, Map<String, Object> report) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(runId)),
                new Update().set("finishedAt", clock.instant()).set("status", status).set("report", report),
                JobRun.class);
    }

    /**
     * Marks every run still {@code RUNNING} that started before {@code startedBefore} as {@code FAILED} with
     * {@code {error: "Interrupted"}} ({@link InterruptedJobRuns}); returns how many there were.
     */
    public long failInterrupted(Instant startedBefore) {
        return mongo.updateMulti(Query.query(Criteria.where("status").is(RUNNING).and("startedAt").lt(startedBefore)),
                new Update().set("finishedAt", clock.instant()).set("status", FAILED)
                        .set("report", Map.of("error", "Interrupted")),
                JobRun.class).getModifiedCount();
    }

    public Optional<JobRun> lastRun(JobKind kind) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("job").is(kind)).with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(1),
                JobRun.class));
    }

    /**
     * Runs {@code body} as a logged job: {@code OK} with its report, or {@code FAILED} with
     * {@code {error: <class simple name>}}, a chat alert and the exception rethrown.
     */
    public Map<String, Object> run(JobKind kind, Supplier<Map<String, Object>> body) {
        String runId = start(kind);
        try {
            Map<String, Object> report = body.get();
            finish(runId, OK, report);
            return report;
        } catch (RuntimeException | Error e) {
            fail(runId, kind, e);
            throw e;
        }
    }

    private void fail(String runId, JobKind kind, Throwable e) {
        String reason = e.getClass().getSimpleName();
        log.error("Job {} failed", kind, e);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("error", reason);
        try {
            finish(runId, FAILED, report);
            gateway.sendText("⚠️ Задача " + kind + " завершилася з помилкою: " + Html.escape(reason));
        } catch (RuntimeException secondary) {
            log.error("Could not record or announce the failure of {}", kind, secondary);
        }
    }
}
