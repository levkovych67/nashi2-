package com.nashi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The two background executors. Always name one: {@code @Async("telegramExecutor")} or {@code @Async("jobsExecutor")}.
 * Each has a single thread, so work of one kind runs in order and never in parallel.
 * <ul>
 *   <li>{@code telegramExecutor}: Telegram sends, queue 500; when full the caller gets {@code TaskRejectedException}.</li>
 *   <li>{@code jobsExecutor}: imports and enrichment, queue 200; when full the job is dropped with a warning,
 *       never failing the caller (for example a moderation callback).</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    static final int TELEGRAM_QUEUE = 500;
    static final int JOBS_QUEUE = 200;

    @Bean
    public ThreadPoolTaskExecutor telegramExecutor() {
        return singleThread("telegram-", TELEGRAM_QUEUE, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public ThreadPoolTaskExecutor jobsExecutor() {
        return singleThread("jobs-", JOBS_QUEUE, (task, pool) ->
                log.warn("jobsExecutor queue is full ({} waiting); a background job was dropped", pool.getQueue().size()));
    }

    /** Failures of {@code void} {@code @Async} methods; arguments are not logged (they may hold personal data). */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("@Async {}.{} failed", method.getDeclaringClass().getSimpleName(), method.getName(), ex);
    }

    private static ThreadPoolTaskExecutor singleThread(String prefix, int queue, RejectedExecutionHandler rejection) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(rejection);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
