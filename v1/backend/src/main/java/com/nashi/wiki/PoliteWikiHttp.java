package com.nashi.wiki;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Wikimedia asks bots to identify themselves, send one request at a time and back off when told to.
 * See https://meta.wikimedia.org/wiki/User-Agent_policy and https://www.mediawiki.org/wiki/API:Etiquette.
 */
@Slf4j
public class PoliteWikiHttp implements WikiHttp {

    public static final String USER_AGENT = "NashiWikiImport/1.0 (https://github.com/levkovych67/nashi-frontend)";
    static final long MIN_GAP_MILLIS = 200;
    static final int MAX_RETRIES = 4;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(70);

    private final HttpClient client;
    private final Sleeper sleeper;
    private long lastRequestNanos;

    public PoliteWikiHttp() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build(), Sleeper.REAL);
    }

    public PoliteWikiHttp(HttpClient client, Sleeper sleeper) {
        this.client = client;
        this.sleeper = sleeper;
    }

    @Override
    public synchronized String get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/sparql-results+json, application/json")
                .GET()
                .build();

        for (int attempt = 0; ; attempt++) {
            keepGap();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("[WIKI] {} failed ({}), retry {}", shorten(url), e.getMessage(), attempt + 1);
                    sleeper.sleep(backoff(attempt));
                    continue;
                }
                throw new WikiFetchException("request failed: " + shorten(url), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WikiFetchException("interrupted: " + shorten(url), e);
            } finally {
                lastRequestNanos = System.nanoTime();
            }

            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            if (isRetryable(status) && attempt < MAX_RETRIES) {
                long wait = retryAfterMillis(response).orElse(backoff(attempt));
                log.warn("[WIKI] HTTP {} for {}, waiting {} ms", status, shorten(url), wait);
                sleeper.sleep(wait);
                continue;
            }
            throw new WikiFetchException("HTTP " + status + " for " + shorten(url), status);
        }
    }

    private void keepGap() {
        if (lastRequestNanos == 0) {
            return;
        }
        long elapsedMillis = (System.nanoTime() - lastRequestNanos) / 1_000_000;
        if (elapsedMillis < MIN_GAP_MILLIS) {
            sleeper.sleep(MIN_GAP_MILLIS - elapsedMillis);
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 503 || status == 504 || status == 502;
    }

    private static long backoff(int attempt) {
        return 2000L << attempt;
    }

    private static Optional<Long> retryAfterMillis(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return Optional.of(Long.parseLong(value.trim()) * 1000L);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static String shorten(String url) {
        return url.length() > 160 ? url.substring(0, 160) + "…" : url;
    }
}
