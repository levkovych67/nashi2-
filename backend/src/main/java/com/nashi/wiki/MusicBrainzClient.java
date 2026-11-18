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
import java.util.function.LongSupplier;

/** MusicBrainz allows one request per second per client; see https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting. */
@Slf4j
public class MusicBrainzClient implements MusicBrainzSource {

    static final String BASE_URL = "https://musicbrainz.org/ws/2/artist/";
    static final long MIN_GAP_MILLIS = 1100;
    static final int MAX_RETRIES = 4;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;
    private final String baseUrl;
    private final String userAgent;
    private long lastRequestNanos;
    private boolean requested;

    /** @param userAgent the project User-Agent ({@code nashi.http.user-agent}), without personal details */
    public MusicBrainzClient(String userAgent) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build(), Sleeper.REAL, System::nanoTime, BASE_URL, userAgent);
    }

    MusicBrainzClient(HttpClient client, Sleeper sleeper, LongSupplier nanoClock, String baseUrl,
                       String userAgent) {
        this.client = client;
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
    }

    @Override
    public synchronized String get(String mbid) {
        String url = baseUrl + mbid + "?inc=url-rels&fmt=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();

        for (int attempt = 0; ; attempt++) {
            keepGap();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("[MB] {} failed ({}), retry {}", mbid, e.getMessage(), attempt + 1);
                    sleeper.sleep(backoff(attempt));
                    continue;
                }
                throw new WikiFetchException("MusicBrainz request failed: " + mbid, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WikiFetchException("interrupted: " + mbid, e);
            } finally {
                lastRequestNanos = nanoClock.getAsLong();
                requested = true;
            }

            int status = response.statusCode();
            if (status == 200) {
                return response.body();
            }
            if ((status == 503 || status == 429) && attempt < MAX_RETRIES) {
                long wait = retryAfterMillis(response).orElse(backoff(attempt));
                log.warn("[MB] HTTP {} for {}, waiting {} ms", status, mbid, wait);
                sleeper.sleep(wait);
                continue;
            }
            throw new WikiFetchException("HTTP " + status + " for MusicBrainz " + mbid, status);
        }
    }

    private void keepGap() {
        if (!requested) {
            return;
        }
        long elapsedMillis = (nanoClock.getAsLong() - lastRequestNanos) / 1_000_000;
        if (elapsedMillis < MIN_GAP_MILLIS) {
            sleeper.sleep(MIN_GAP_MILLIS - elapsedMillis);
        }
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
}
