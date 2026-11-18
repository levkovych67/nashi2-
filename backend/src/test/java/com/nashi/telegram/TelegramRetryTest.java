package com.nashi.telegram;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.api.objects.ResponseParameters;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramRetryTest {

    private final List<Long> sleeps = new ArrayList<>();
    private final TelegramRetry retry = new TelegramRetry(sleeps::add);

    private static TelegramApiRequestException coded(int code, Integer retryAfter) {
        ApiResponse<Object> response = new ApiResponse<>(false, code, "failed",
                retryAfter == null ? null : params(retryAfter), null);
        return new TelegramApiRequestException("failed", response);
    }

    private static ResponseParameters params(int retryAfter) {
        return new ResponseParameters(null, retryAfter);
    }

    @Test
    void retryHonoursRetryAfter() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw coded(429, 3);
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(sleeps).containsExactly(3000L);
        assertThat(calls).hasValue(2);
    }

    @Test
    void retryStopsOn400() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw coded(400, null);
        })).isInstanceOf(TelegramApiRequestException.class);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retryBacksOffOnServerErrorsAndGivesUpAfterFiveAttempts() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw coded(502, null);
        })).isInstanceOf(TelegramApiRequestException.class);
        assertThat(calls).hasValue(5);
        assertThat(sleeps).containsExactly(1000L, 2000L, 4000L, 8000L);
    }

    @Test
    void retriesOnNetworkIoFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = retry.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new TelegramApiException("connection reset", new java.io.IOException("reset"));
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(sleeps).containsExactly(1000L, 2000L);
    }

    @Test
    void interruptWhileSleepingRestoresFlagAndStops() {
        TelegramRetry interrupted = new TelegramRetry(millis -> {
            throw new InterruptedException();
        });
        try {
            assertThatThrownBy(() -> interrupted.execute(() -> {
                throw coded(429, 1);
            })).isInstanceOf(TelegramApiException.class).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void retryStopsOnValidationError() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.execute(() -> {
            calls.incrementAndGet();
            throw new org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException("too long", (org.telegram.telegrambots.meta.api.interfaces.BotApiObject) null);
        })).isInstanceOf(org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException.class);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }
}
