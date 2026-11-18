package com.nashi.jobs;

import com.nashi.support.MongoIT;
import com.nashi.telegram.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
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
}
