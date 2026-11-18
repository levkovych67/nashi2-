package com.nashi.audio;

import com.google.gson.JsonObject;
import com.nashi.http.HttpFetchException;
import com.nashi.http.PoliteHttp;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonTest {

    private HttpServer server;
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<Long> pauses = new ArrayList<>();
    /** A clock that only moves when the code under test pauses, so gap arithmetic is exact. */
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final PoliteHttp.Pause pause = millis -> {
        pauses.add(millis);
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

    private HttpJson json() {
        return new HttpJson(new PoliteHttp(HttpClient.newHttpClient(), pause, nanos::get,
                "NashiBot/2.0 (+https://nashi.example/about)", Duration.ofSeconds(1)));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void respond(String path, int status, String body, String... headers) {
        server.createContext(path, exchange -> {
            requests.incrementAndGet();
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            for (int i = 0; i < headers.length; i += 2) {
                exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    void parsesJsonAndSendsTheConfiguredUserAgent() {
        respond("/ok", 200, "{\"data\":[{\"id\":7,\"title\":\"Привіт\"}]}");

        JsonObject body = json().get(url("/ok"));

        assertThat(body.getAsJsonArray("data").get(0).getAsJsonObject().get("title").getAsString()).isEqualTo("Привіт");
        assertThat(userAgents).containsExactly("NashiBot/2.0 (+https://nashi.example/about)");
    }

    @Test
    void keepsAGapPerHost() {
        respond("/a", 200, "{}");
        HttpJson json = json();

        json.get(url("/a"));
        assertThat(pauses).isEmpty();
        json.get(url("/a"));

        assertThat(pauses).containsExactly(1000L);
    }

    @Test
    void honoursRetryAfterOn429() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/limited", exchange -> {
            int n = calls.incrementAndGet();
            byte[] bytes = (n == 1 ? "" : "{\"ok\":true}").getBytes(StandardCharsets.UTF_8);
            if (n == 1) {
                exchange.getResponseHeaders().add("Retry-After", "5");
            }
            exchange.sendResponseHeaders(n == 1 ? 429 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        JsonObject body = json().get(url("/limited"));

        assertThat(body.get("ok").getAsBoolean()).isTrue();
        assertThat(calls).hasValue(2);
        assertThat(pauses).contains(5000L);
    }

    @Test
    void capsRetryAfterAtSixtySeconds() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/busy", exchange -> {
            int n = calls.incrementAndGet();
            byte[] bytes = (n == 1 ? "" : "{}").getBytes(StandardCharsets.UTF_8);
            if (n == 1) {
                exchange.getResponseHeaders().add("Retry-After", "3600");
            }
            exchange.sendResponseHeaders(n == 1 ? 503 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        json().get(url("/busy"));

        assertThat(pauses).containsExactly(60_000L);
    }

    @Test
    void givesUpOnPersistentErrorsAndReportsTheStatus() {
        respond("/down", 503, "", "Retry-After", "1");

        assertThatThrownBy(() -> json().get(url("/down")))
                .isInstanceOf(HttpFetchException.class).hasMessageContaining("503");
        assertThat(requests.get()).isEqualTo(1 + PoliteHttp.MAX_RETRIES);
    }

    @Test
    void doesNotRetryOtherErrors() {
        respond("/missing", 404, "{}");

        assertThatThrownBy(() -> json().get(url("/missing")))
                .isInstanceOf(HttpFetchException.class).hasMessageContaining("404");
        assertThat(requests).hasValue(1);
    }

    @Test
    void rejectsABodyThatIsNotAJsonObject() {
        respond("/list", 200, "[1,2]");

        assertThatThrownBy(() -> json().get(url("/list"))).isInstanceOf(HttpFetchException.class);
    }

    @Test
    void usesATenSecondTimeout() {
        assertThat(PoliteHttp.TIMEOUT).isEqualTo(Duration.ofSeconds(10));
        assertThat(PoliteHttp.MAX_RETRY_AFTER).isEqualTo(Duration.ofSeconds(60));
    }
}
