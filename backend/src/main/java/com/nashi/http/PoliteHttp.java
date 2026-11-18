package com.nashi.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * The shared manners of the project's outgoing JSON clients: the project User-Agent (no personal details), at least
 * {@code gap} between two requests to the same host, a 10 s timeout, and on 429/503 a wait of {@code Retry-After}
 * (at most 60 s) before retrying up to {@value #MAX_RETRIES} times. Calls are serialized per instance.
 * <p>
 * {@code PoliteWikiHttp} has its own, stricter policy for Wikimedia (70 s timeout, four retries with exponential
 * backoff, one global gap) and is deliberately not built on this class.
 */
public class PoliteHttp {

    private static final Logger log = LoggerFactory.getLogger(PoliteHttp.class);

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);
    public static final int MAX_RETRIES = 2;

    /** Waits between requests; replaced in tests so they do not wait. */
    @FunctionalInterface
    public interface Pause {
        void pause(long millis);

        Pause REAL = millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpFetchException("interrupted while waiting", e);
            }
        };
    }

    private final HttpClient client;
    private final Pause pause;
    private final LongSupplier nanoTime;
    private final String userAgent;
    private final long gapMillis;
    private final Map<String, Long> lastRequestNanosByHost = new HashMap<>();

    public PoliteHttp(String userAgent, Duration gap) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(TIMEOUT).build(),
                Pause.REAL, System::nanoTime, userAgent, gap);
    }

    public PoliteHttp(HttpClient client, Pause pause, LongSupplier nanoTime, String userAgent, Duration gap) {
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("User-Agent must not be blank");
        }
        if (gap == null || gap.isNegative()) {
            throw new IllegalArgumentException("Request gap must be zero or positive: " + gap);
        }
        this.client = Objects.requireNonNull(client);
        this.pause = Objects.requireNonNull(pause);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.userAgent = userAgent;
        this.gapMillis = gap.toMillis();
    }

    public String userAgent() {
        return userAgent;
    }

    /** GETs {@code url} and returns the body of the 200 response. */
    public synchronized String get(String url, String accept) {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", userAgent)
                .header("Accept", accept).GET().build();

        for (int attempt = 0; ; attempt++) {
            keepGap(host);
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new HttpFetchException("request failed: " + host, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HttpFetchException("interrupted: " + host, e);
            } finally {
                lastRequestNanosByHost.put(host, nanoTime.getAsLong());
            }

            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            if ((status == 429 || status == 503) && attempt < MAX_RETRIES) {
                long wait = retryAfterMillis(response).orElse(1000L * (attempt + 1));
                log.warn("[HTTP] {} answered {}, waiting {} ms", host, status, wait);
                pause.pause(wait);
                continue;
            }
            throw new HttpFetchException("HTTP " + status + " from " + host, status);
        }
    }

    private void keepGap(String host) {
        Long last = lastRequestNanosByHost.get(host);
        if (last == null) {
            return;
        }
        long elapsedMillis = (nanoTime.getAsLong() - last) / 1_000_000;
        if (elapsedMillis < gapMillis) {
            pause.pause(gapMillis - elapsedMillis);
        }
    }

    private static Optional<Long> retryAfterMillis(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                long seconds = Math.max(0, Long.parseLong(value.trim()));
                return Optional.of(Math.min(seconds * 1000L, MAX_RETRY_AFTER.toMillis()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }
}
