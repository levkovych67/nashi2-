package com.nashi.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.IOException;

/**
 * Runs a Bot API call with at most {@value #MAX_ATTEMPTS} attempts: 429 waits {@code retry_after}; 5xx and
 * network (I/O) errors wait 1, 2, 4, 8 s; everything else (other 4xx, validation, parse errors) fails at once.
 */
@Component
public class TelegramRetry {

    /** Waits between attempts; a test passes one that records the delay. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** One Bot API call; a lambda over a checked-throwing client method does not fit {@code Supplier}. */
    @FunctionalInterface
    public interface Call<T> {
        T run() throws TelegramApiException;
    }

    static final int MAX_ATTEMPTS = 5;
    private static final long BASE_BACKOFF_MILLIS = 1000;
    private static final Logger log = LoggerFactory.getLogger(TelegramRetry.class);

    private final Sleeper sleeper;

    @Autowired
    public TelegramRetry() {
        this(Thread::sleep);
    }

    public TelegramRetry(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    public <T> T execute(Call<T> call) throws TelegramApiException {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.run();
            } catch (TelegramApiException e) {
                long waitMillis = waitMillis(e, attempt);
                if (waitMillis < 0 || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Telegram call failed (attempt {}/{}), retrying in {} ms: {}",
                        attempt, MAX_ATTEMPTS, waitMillis, e.getMessage());
                try {
                    sleeper.sleep(waitMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TelegramApiException("interrupted while waiting to retry", ie);
                }
            }
        }
    }

    /** Milliseconds to wait before retrying, or -1 when the failure is final. */
    private static long waitMillis(TelegramApiException e, int attempt) {
        if (e instanceof TelegramApiRequestException request) {
            Integer code = request.getErrorCode();
            if (code != null && code == 429) {
                Integer retryAfter = request.getParameters() == null ? null : request.getParameters().getRetryAfter();
                return (retryAfter == null ? 1 : retryAfter) * 1000L;
            }
            if (code != null && code >= 500) {
                return backoff(attempt);
            }
        }
        // Validation and parse failures are deterministic; only a real I/O failure is worth another try.
        return causedByIo(e) ? backoff(attempt) : -1;
    }

    private static boolean causedByIo(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private static long backoff(int attempt) {
        return BASE_BACKOFF_MILLIS << (attempt - 1);
    }
}
