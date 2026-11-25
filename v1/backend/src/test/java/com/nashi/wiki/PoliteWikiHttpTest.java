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
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PoliteWikiHttpTest {

    private HttpServer server;
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();
    private PoliteWikiHttp http;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        http = new PoliteWikiHttp(HttpClient.newHttpClient(), sleeps::add);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
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
    void sendsTheProjectUserAgent() {
        respond("/ok", 200);

        assertEquals("{\"ok\":true}", http.get(url("/ok")));
        assertEquals("NashiWikiImport/1.0 (https://github.com/levkovych67/nashi-frontend)", userAgents.get(0));
    }

    @Test
    void waitsForRetryAfterOnTooManyRequests() {
        respond("/limited", 429, 200);

        assertEquals("{\"ok\":true}", http.get(url("/limited")));
        assertEquals(2, requests.get());
        assertTrue(sleeps.contains(3000L), "slept " + sleeps);
    }

    @Test
    void givesUpAfterFourRetries() {
        respond("/down", 503);

        WikiFetchException e = assertThrows(WikiFetchException.class, () -> http.get(url("/down")));
        assertEquals(5, requests.get());
        assertEquals(OptionalInt.of(503), e.httpStatus());
        assertFalse(e.timedOut());
        assertTrue(sleeps.containsAll(List.of(2000L, 4000L, 8000L, 16000L)), "slept " + sleeps);
    }

    @Test
    void doesNotRetryWhenThePageIsMissing() {
        respond("/missing", 404);

        WikiFetchException e = assertThrows(WikiFetchException.class, () -> http.get(url("/missing")));
        assertEquals(1, requests.get());
        assertEquals(OptionalInt.of(404), e.httpStatus());
    }

    @Test
    void connectionFailureHasNoStatusAndIsNoTimeout() {
        String closed = url("/gone");
        server.stop(0);

        WikiFetchException e = assertThrows(WikiFetchException.class, () -> http.get(closed));

        assertEquals(OptionalInt.empty(), e.httpStatus());
        assertFalse(e.timedOut());
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void reportsTheClientTimeout() {
        WikiFetchException e = new WikiFetchException("request failed: x", new HttpTimeoutException("t"));

        assertTrue(e.timedOut());
        assertEquals(OptionalInt.empty(), e.httpStatus());
    }
}
