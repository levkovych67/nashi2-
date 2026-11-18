package com.nashi.audio;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeezerPreviewUrlsTest {

    private static final String PREVIEW = "https://cdnt-preview.dzcdn.net/api/1/1/a.mp3?hdnea=exp=1794910200~acl=/api/1/1/a.mp3*~hmac=x";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private volatile String body = "{\"id\":1,\"preview\":\"" + PREVIEW + "\"}";
    private volatile int status = 200;
    private volatile long delayMs = 0;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T09:00:00Z"));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/track/", ex -> {
            calls.incrementAndGet();
            userAgent.set(ex.getRequestHeaders().getFirst("User-Agent"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DeezerPreviewUrls urls(int perWindow) {
        return new DeezerPreviewUrls("http://127.0.0.1:" + server.getAddress().getPort(), clock, "TestBot/1", perWindow);
    }

    @Test
    void deezerExpiryParsedFromHdnea() {
        DeezerPreviewUrls.PreviewUrl url = urls(40).fresh("1").orElseThrow();
        assertThat(url.url()).isEqualTo(PREVIEW);
        assertThat(url.expiresAt()).isEqualTo(Instant.parse("2026-11-17T10:09:00Z"));
        assertThat(userAgent.get()).isEqualTo("TestBot/1");
    }

    @Test
    void withoutHdneaExpiresInTenMinutes() {
        body = "{\"preview\":\"https://cdn/x.mp3\"}";
        assertThat(urls(40).fresh("1").orElseThrow().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
    }

    @Test
    void deezerCachedUntilExpiry() {
        DeezerPreviewUrls urls = urls(40);
        urls.fresh("1");
        urls.fresh("1");
        assertThat(calls.get()).isEqualTo(1);
        clock.set(Instant.parse("2026-11-17T10:08:59Z"));
        urls.fresh("1");
        assertThat(calls.get()).isEqualTo(1);
        clock.set(Instant.parse("2026-11-17T10:09:01Z"));
        urls.fresh("1");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void failuresAreNotCached() {
        DeezerPreviewUrls urls = urls(40);
        status = 500;
        assertThat(urls.fresh("1")).isEmpty();
        status = 200;
        assertThat(urls.fresh("1")).isPresent();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void missingPreviewAndErrorBodyGiveEmpty() {
        body = "{\"error\":{\"type\":\"DataException\"}}";
        assertThat(urls(40).fresh("1")).isEmpty();
        body = "{\"preview\":\"\"}";
        assertThat(urls(40).fresh("2")).isEmpty();
    }

    @Test
    void slowUpstreamTimesOutToEmpty() {
        delayMs = 3500;
        long t0 = System.nanoTime();
        assertThat(urls(40).fresh("1")).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofMillis(3000));
    }

    @Test
    void deezerConcurrentRequestsShareOneUpstreamCall() throws Exception {
        delayMs = 300;
        DeezerPreviewUrls urls = urls(40);
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Optional<DeezerPreviewUrls.PreviewUrl>>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return urls.fresh("7");
            }));
        }
        go.countDown();
        for (Future<Optional<DeezerPreviewUrls.PreviewUrl>> f : futures) {
            assertThat(f.get()).isPresent();
        }
        pool.shutdown();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rateLimitReturnsEmptyWithoutCallingUpstream() {
        DeezerPreviewUrls urls = urls(2);
        assertThat(urls.fresh("1")).isPresent();
        assertThat(urls.fresh("2")).isPresent();
        assertThat(urls.fresh("3")).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(urls.fresh("1")).isPresent(); // cached, needs no token
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
