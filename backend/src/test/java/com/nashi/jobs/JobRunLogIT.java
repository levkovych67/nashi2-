package com.nashi.jobs;

import com.nashi.support.MongoIT;
import com.nashi.telegram.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobRunLogIT extends MongoIT {

    @Autowired
    private MongoTemplate mongo;
    @Autowired
    private Clock clock;

    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private JobRunLog runLog;

    @BeforeEach
    void setUp() {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), JobRun.class);
        runLog = new JobRunLog(mongo, clock, gateway);
    }

    @Test
    void startAndFinishAreVisibleInLastRun() {
        String id = runLog.start(JobKind.WIKI_IMPORT);
        assertThat(runLog.lastRun(JobKind.WIKI_IMPORT)).get()
                .satisfies(r -> {
                    assertThat(r.getId()).isEqualTo(id);
                    assertThat(r.getStatus()).isEqualTo("RUNNING");
                    assertThat(r.getFinishedAt()).isNull();
                });
        runLog.finish(id, "OK", Map.of("n", 3));
        JobRun run = runLog.lastRun(JobKind.WIKI_IMPORT).orElseThrow();
        assertThat(run.getStatus()).isEqualTo("OK");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getReport()).containsEntry("n", 3);
        assertThat(runLog.lastRun(JobKind.AUDIO_RESOLVE)).isEmpty();
    }

    @Test
    void failedRunIsLoggedAndAlertedWithClassNameOnly() {
        assertThatThrownBy(() -> runLog.run(JobKind.AUDIO_RESOLVE, () -> {
            throw new IllegalStateException("secret-token-123");
        })).isInstanceOf(IllegalStateException.class);

        JobRun run = runLog.lastRun(JobKind.AUDIO_RESOLVE).orElseThrow();
        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getReport()).containsEntry("error", "IllegalStateException");
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(gateway).sendText(text.capture());
        assertThat(text.getValue()).isEqualTo(
                "⚠️ Задача AUDIO_RESOLVE завершилася з помилкою: IllegalStateException");
        assertThat(text.getValue()).doesNotContain("secret");
    }

    @Test
    void successfulRunDoesNotAlert() {
        runLog.run(JobKind.V1_MIGRATION, () -> Map.of("ok", true));
        assertThat(runLog.lastRun(JobKind.V1_MIGRATION).orElseThrow().getStatus()).isEqualTo("OK");
        verify(gateway, never()).sendText(org.mockito.ArgumentMatchers.anyString());
    }

    private String insert(JobKind kind, String status, Instant startedAt) {
        JobRun run = new JobRun();
        run.setJob(kind);
        run.setStatus(status);
        run.setStartedAt(startedAt);
        return mongo.insert(run).getId();
    }

    @Test
    void runsLeftRunningByThePreviousProcessAreFailedAtStartup() {
        Instant boot = Instant.parse("2026-11-18T10:00:00Z");
        String stale = insert(JobKind.WIKI_IMPORT, "RUNNING", boot.minus(Duration.ofHours(1)));
        String done = insert(JobKind.AUDIO_RESOLVE, "OK", boot.minus(Duration.ofHours(2)));
        // started by this process (e.g. an admin command right after the bot connected): still running
        String current = insert(JobKind.V1_MIGRATION, "RUNNING", boot.plusSeconds(1));

        new InterruptedJobRuns(runLog, Clock.fixed(boot, ZoneOffset.UTC)).run(null);

        JobRun failed = mongo.findById(stale, JobRun.class);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getReport()).isEqualTo(Map.of("error", "Interrupted"));
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(mongo.findById(done, JobRun.class).getStatus()).isEqualTo("OK");
        assertThat(mongo.findById(done, JobRun.class).getReport()).isNull();
        assertThat(mongo.findById(current, JobRun.class).getStatus()).isEqualTo("RUNNING");
        verify(gateway, never()).sendText(org.mockito.ArgumentMatchers.anyString());
    }
}
