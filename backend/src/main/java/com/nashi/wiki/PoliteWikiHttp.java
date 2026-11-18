package com.nashi.wiki;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Wikimedia asks bots to identify themselves, send one request at a time and back off when told to
 * (https://meta.wikimedia.org/wiki/User-Agent_policy, https://www.mediawiki.org/wiki/API:Etiquette). This client
 * sends a project User-Agent without personal details, keeps at least {@code requestGap} between the end of one
 * request and the start of the next, and on 429/502/503/504 waits {@code Retry-After} (or an exponential backoff)
 * before retrying up to {@value #MAX_RETRIES} times. Calls are serialized: one instance never sends two requests at
 * once, whether for text ({@link #get}) or for a file ({@link #getFile}), so the gap holds across both.
 * <p>
 * In Spring the User-Agent and the gap come from {@code nashi.http.user-agent} and {@code nashi.wiki.request-gap}
 * ({@link WikiHttpConfig}); stand-alone tools pass them in.
 */
public class PoliteWikiHttp implements WikiHttp, WikiFileHttp {

    private static final Logger log = LoggerFactory.getLogger(PoliteWikiHttp.class);

    /** Plan Global Constraints (D-52): no personal e-mail or handle. Production sets its own domain. */
    public static final String DEFAULT_USER_AGENT = "NashiBot/2.0 (+https://nashi.example/about)";
    /** Plan Global Constraints (D-52): a pause of at least one second between requests. */
    public static final Duration DEFAULT_REQUEST_GAP = Duration.ofSeconds(1);

    static final int MAX_RETRIES = 4;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(70);

    private final HttpClient client;
    private final Sleeper sleeper;
    private final String userAgent;
    private final long gapMillis;
    private final LongSupplier nanoTime;
    private long lastRequestNanos;
    private boolean anyRequest;

    public PoliteWikiHttp() {
        this(DEFAULT_USER_AGENT, DEFAULT_REQUEST_GAP);
    }

    public PoliteWikiHttp(String userAgent, Duration requestGap) {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build(), Sleeper.REAL, userAgent, requestGap);
    }

    public PoliteWikiHttp(HttpClient client, Sleeper sleeper, String userAgent, Duration requestGap) {
        this(client, sleeper, userAgent, requestGap, System::nanoTime);
    }

    PoliteWikiHttp(HttpClient client, Sleeper sleeper, String userAgent, Duration requestGap, LongSupplier nanoTime) {
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("User-Agent must not be blank");
        }
        if (requestGap == null || requestGap.isNegative()) {
            throw new IllegalArgumentException("Request gap must be zero or positive: " + requestGap);
        }
        this.client = Objects.requireNonNull(client);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.userAgent = userAgent;
        this.gapMillis = requestGap.toMillis();
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public String userAgent() {
        return userAgent;
    }

    public Duration requestGap() {
        return Duration.ofMillis(gapMillis);
    }

    @Override
    public synchronized String get(String url) {
        return send(url, "application/sparql-results+json, application/json",
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    @Override
    public synchronized WikiFile getFile(String url) {
        HttpResponse<byte[]> response = send(url, "image/*", HttpResponse.BodyHandlers.ofByteArray());
        String contentType = response.headers().firstValue("Content-Type")
                .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .orElse(null);
        return new WikiFile(response.body(), contentType);
    }

    private <T> HttpResponse<T> send(String url, String accept, HttpResponse.BodyHandler<T> bodyHandler) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .GET()
                .build();

        for (int attempt = 0; ; attempt++) {
            keepGap();
            HttpResponse<T> response;
            try {
                response = client.send(request, bodyHandler);
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
                lastRequestNanos = nanoTime.getAsLong();
                anyRequest = true;
            }

            int status = response.statusCode();
            if (status == 200) {
                return response;
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
        if (!anyRequest) {
            return;
        }
        long elapsedMillis = (nanoTime.getAsLong() - lastRequestNanos) / 1_000_000;
        if (elapsedMillis < gapMillis) {
            sleeper.sleep(gapMillis - elapsedMillis);
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
                return Optional.of(Math.max(0, Long.parseLong(value.trim())) * 1000L);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static String shorten(String url) {
        return url.length() > 160 ? url.substring(0, 160) + "…" : url;
    }
}
