package com.nashi.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();
    private final List<ThreadPoolTaskExecutor> started = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void stop() {
        release.countDown();
        started.forEach(ThreadPoolTaskExecutor::shutdown);
    }

    @Test
    void executorsAreSingleThreaded() {
        for (ThreadPoolTaskExecutor executor : List.of(start(config.telegramExecutor()), start(config.jobsExecutor()))) {
            assertEquals(1, executor.getCorePoolSize());
            assertEquals(1, executor.getMaxPoolSize());
        }
        assertEquals(500, config.telegramExecutor().getQueueCapacity());
        assertEquals(200, config.jobsExecutor().getQueueCapacity());
    }

    @Test
    void jobsExecutorDropsWorkWhenFullInsteadOfFailingTheCaller() throws Exception {
        ThreadPoolTaskExecutor jobs = start(config.jobsExecutor());
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch busy = occupyTheThread(jobs);

        for (int i = 0; i < AsyncConfig.JOBS_QUEUE + 5; i++) {
            assertDoesNotThrow(() -> jobs.execute(ran::incrementAndGet));
        }
        release.countDown();
        jobs.shutdown();

        assertTrue(busy.await(1, TimeUnit.SECONDS));
        assertTrue(jobs.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(AsyncConfig.JOBS_QUEUE, ran.get());
    }

    @Test
    void telegramExecutorRejectsWhenFull() {
        ThreadPoolTaskExecutor telegram = start(config.telegramExecutor());
        occupyTheThread(telegram);

        for (int i = 0; i < AsyncConfig.TELEGRAM_QUEUE; i++) {
            telegram.execute(() -> { });
        }

        assertThrows(TaskRejectedException.class, () -> telegram.execute(() -> { }));
    }

    private ThreadPoolTaskExecutor start(ThreadPoolTaskExecutor executor) {
        executor.initialize();
        started.add(executor);
        return executor;
    }

    /** Blocks the single worker until the test releases it, so later tasks wait in the queue. */
    private CountDownLatch occupyTheThread(ThreadPoolTaskExecutor executor) {
        CountDownLatch running = new CountDownLatch(1);
        executor.execute(() -> {
            running.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(running.await(5, TimeUnit.SECONDS), "worker did not start");
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return running;
    }
}
