package com.nashi.audio;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fresh Deezer preview URLs for the fragment endpoint (a hot path: a map-pin hover). A preview link is signed and
 * expires, so it is fetched from {@code GET /track/{id}} and cached per track until shortly before it expires.
 * <p>
 * Unlike the batch jobs ({@link HttpJson}) this has its own client: 2 s timeout, no politeness sleep, concurrent
 * requests for one track share one upstream call ({@code Cache.get} computes a key once), and a global limit of
 * 40 upstream calls per 5 s (Deezer allows 50). When the limit is hit or the call fails or times out the result is
 * empty and the caller falls back to another clip. Failures are not cached at all: a failed track is retried by the
 * next request, which costs a token from the same limit, so a failing Deezer cannot be hammered.
 */
@Component
public class DeezerPreviewUrls {

    public record PreviewUrl(String url, Instant expiresAt) {
    }

    static final String BASE_URL = "https://api.deezer.com";
    static final int REQUESTS_PER_WINDOW = 40;
    private static final Duration WINDOW = Duration.ofSeconds(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(60);
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(10);
    private static final Pattern HDNEA_EXP = Pattern.compile("hdnea=(?:exp=|exp%3D)(\\d+)");

    private final String baseUrl;
    private final Clock clock;
    private final String userAgent;
    private final HttpClient http;
    private final Bucket bucket;
    private final Cache<String, PreviewUrl> cache;

    @Autowired
    public DeezerPreviewUrls(Clock clock, @Value("${nashi.http.user-agent:" + HttpJson.DEFAULT_USER_AGENT + "}") String userAgent) {
        this(BASE_URL, clock, userAgent, REQUESTS_PER_WINDOW);
    }

    DeezerPreviewUrls(String baseUrl, Clock clock, String userAgent, int requestsPerWindow) {
        this.baseUrl = baseUrl;
        this.clock = clock;
        this.userAgent = userAgent;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.bucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(requestsPerWindow).refillIntervally(requestsPerWindow, WINDOW).build())
                .build();
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .expireAfter(new Expiry<String, PreviewUrl>() {
                    @Override
                    public long expireAfterCreate(String key, PreviewUrl value, long currentTime) {
                        return Math.max(0, Duration.between(clock.instant(), value.expiresAt()).toNanos());
                    }

                    @Override
                    public long expireAfterUpdate(String key, PreviewUrl value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, PreviewUrl value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /** A usable preview URL for the track, or empty when Deezer has none, is slow or failing, or the limit is hit. */
    public Optional<PreviewUrl> fresh(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return Optional.empty();
        }
        // A null from the loader is not stored, so failures are never cached.
        return Optional.ofNullable(cache.get(trackId, this::load));
    }

    private PreviewUrl load(String trackId) {
        if (!bucket.tryConsume(1)) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/track/"
                            + URLEncoder.encode(trackId, StandardCharsets.UTF_8)))
                    .timeout(TIMEOUT).header("User-Agent", userAgent).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            Optional<String> preview = Json.string(json, "preview");
            if (preview.isEmpty()) {
                return null;
            }
            Instant expiresAt = expiry(preview.get());
            return expiresAt.isAfter(clock.instant()) ? new PreviewUrl(preview.get(), expiresAt) : null;
        } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Instant expiry(String url) {
        Matcher m = HDNEA_EXP.matcher(url);
        if (m.find()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(m.group(1))).minus(SAFETY_MARGIN);
            } catch (NumberFormatException | java.time.DateTimeException ignored) {
                // falls through to the default
            }
        }
        return clock.instant().plus(FALLBACK_TTL);
    }
}
