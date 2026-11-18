package com.nashi.wiki;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliteWikiHttpTest {

    private HttpServer server;
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();
    /** A clock that only moves when the code under test sleeps, so gap arithmetic is exact. */
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final Sleeper sleeper = millis -> {
        sleeps.add(millis);
        nanos.addAndGet(millis * 1_000_000L);
    };

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private PoliteWikiHttp http() {
        return http(PoliteWikiHttp.DEFAULT_USER_AGENT, PoliteWikiHttp.DEFAULT_REQUEST_GAP);
    }

    private PoliteWikiHttp http(String userAgent, Duration gap) {
        return new PoliteWikiHttp(HttpClient.newHttpClient(), sleeper, userAgent, gap, nanos::get);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void respond(String path, int... statuses) {
        server.createContext(path, exchange -> {
            int n = requests.getAndIncrement();
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            int status = statuses[Math.min(n, statuses.length - 1)];
            if (status == 429) {
                exchange.getResponseHeaders().add("Retry-After", "3");
            }
            byte[] body = (status == 200 ? "{\"ok\":true}" : "busy").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Test
    void sendsTheProjectUserAgentWithoutPersonalDetails() {
        respond("/ok", 200);

        assertThat(http().get(url("/ok"))).isEqualTo("{\"ok\":true}");
        assertThat(userAgents.get(0)).isEqualTo("NashiBot/2.0 (+https://nashi.example/about)");
    }

    @Test
    void userAgentIsConfigurable() {
        respond("/ok", 200);

        http("NashiBot/2.0 (+https://nashi.org.ua/about)", Duration.ofSeconds(1)).get(url("/ok"));

        assertThat(userAgents).containsExactly("NashiBot/2.0 (+https://nashi.org.ua/about)");
    }

    @Test
    void defaultGapIsOneSecond() {
        assertThat(PoliteWikiHttp.DEFAULT_REQUEST_GAP).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void waitsTheGapBetweenRequests() {
        respond("/ok", 200);
        PoliteWikiHttp http = http();

        http.get(url("/ok"));
        nanos.addAndGet(300_000_000L);
        http.get(url("/ok"));

        assertThat(requests.get()).isEqualTo(2);
        assertThat(sleeps).containsExactly(700L);
    }

    @Test
    void gapIsConfigurable() {
        respond("/ok", 200);
        PoliteWikiHttp http = http(PoliteWikiHttp.DEFAULT_USER_AGENT, Duration.ofMillis(2500));

        http.get(url("/ok"));
        http.get(url("/ok"));

        assertThat(sleeps).containsExactly(2500L);
    }

    @Test
    void doesNotWaitBeforeTheFirstRequestOrAfterALongPause() {
        respond("/ok", 200);
        PoliteWikiHttp http = http();

        http.get(url("/ok"));
        nanos.addAndGet(5_000_000_000L);
        http.get(url("/ok"));

        assertThat(sleeps).isEmpty();
    }

    @Test
    void waitsForRetryAfterOnTooManyRequests() {
        respond("/limited", 429, 200);

        assertThat(http().get(url("/limited"))).isEqualTo("{\"ok\":true}");
        assertThat(requests.get()).isEqualTo(2);
        assertThat(sleeps).contains(3000L);
    }

    @Test
    void givesUpAfterFourRetries() {
        respond("/down", 503);

        assertThatThrownBy(() -> http().get(url("/down")))
                .isInstanceOfSatisfying(WikiFetchException.class, e -> {
                    assertThat(e.httpStatus()).isEqualTo(OptionalInt.of(503));
                    assertThat(e.timedOut()).isFalse();
                });
        assertThat(requests.get()).isEqualTo(5);
        assertThat(sleeps).contains(2000L, 4000L, 8000L, 16000L);
    }

    @Test
    void doesNotRetryWhenThePageIsMissing() {
        respond("/missing", 404);

        assertThatThrownBy(() -> http().get(url("/missing")))
                .isInstanceOfSatisfying(WikiFetchException.class,
                        e -> assertThat(e.httpStatus()).isEqualTo(OptionalInt.of(404)));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void connectionFailureHasNoStatusAndIsNoTimeout() {
        String closed = url("/gone");
        server.stop(0);

        assertThatThrownBy(() -> http().get(closed))
                .isInstanceOfSatisfying(WikiFetchException.class, e -> {
                    assertThat(e.httpStatus()).isEmpty();
                    assertThat(e.timedOut()).isFalse();
                    assertThat(e.getCause()).isInstanceOf(IOException.class);
                });
    }

    @Test
    void reportsTheClientTimeout() {
        WikiFetchException e = new WikiFetchException("request failed: x", new HttpTimeoutException("t"));

        assertThat(e.timedOut()).isTrue();
        assertThat(e.httpStatus()).isEmpty();
    }
}
