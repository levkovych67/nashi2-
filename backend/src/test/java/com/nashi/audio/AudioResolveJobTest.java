package com.nashi.audio;

import com.nashi.admin.JobTrigger;
import com.nashi.jobs.JobRunLog;
import com.nashi.moderation.AudioResolveRequested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AudioResolveJobTest {

    private final List<Runnable> queued = new ArrayList<>();
    private boolean reject;
    private final Executor executor = task -> {
        if (reject) {
            throw new RejectedExecutionException("full");
        }
        queued.add(task);
    };
    private final AudioResolveJob job = new AudioResolveJob(mock(MongoTemplate.class), mock(JobRunLog.class),
            new AudioResolver(List.of(), AudioPolicy.of(true, true, true)), AudioPolicy.of(true, true, true),
            mock(ApplicationEventPublisher.class), Clock.systemUTC(), executor);

    @Test
    void startsOneRunAtATimeAndFreesTheFlagWhenItEnds() {
        assertThat(job.startInBackground()).isTrue();
        assertThat(job.startInBackground()).isFalse();

        queued.get(0).run();

        assertThat(job.startInBackground()).isTrue();
    }

    @Test
    void aRejectedExecutorDoesNotLeaveTheJobStuck() {
        reject = true;
        assertThat(job.startInBackground()).isFalse();

        reject = false;
        assertThat(job.startInBackground()).isTrue();
    }

    @Test
    void isTheAudioResolveTrigger() {
        assertThat(job).isInstanceOf(JobTrigger.class);
        assertThat(AudioResolveJob.class.getAnnotation(Qualifier.class).value()).isEqualTo("audioResolve");
    }

    @Test
    void runsNightlyAtThreeKyivTime() throws NoSuchMethodException {
        Scheduled scheduled = AudioResolveJob.class.getMethod("nightly").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 3 * * *");
        assertThat(scheduled.zone()).isEqualTo("Europe/Kyiv");
        ZoneId kyiv = ZoneId.of(scheduled.zone());
        assertThat(CronExpression.parse(scheduled.cron()).next(ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, kyiv)))
                .isEqualTo(ZonedDateTime.of(2026, 11, 19, 3, 0, 0, 0, kyiv));
    }

    @Test
    void theRequestListenerRunsOnTheJobsExecutor() throws NoSuchMethodException {
        var method = AudioResolveJob.class.getMethod("onRequested", AudioResolveRequested.class);

        assertThat(method.getAnnotation(EventListener.class)).isNotNull();
        assertThat(method.getAnnotation(Async.class).value()).isEqualTo("jobsExecutor");
    }
}
