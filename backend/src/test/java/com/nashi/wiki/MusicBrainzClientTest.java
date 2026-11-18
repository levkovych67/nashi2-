package com.nashi.wiki;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MusicBrainzClientTest {

    private HttpServer server;
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final List<String> accepts = new CopyOnWriteArrayList<>();
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();
    /** A clock the fake sleeper moves forward, so pacing is measured without waiting. */
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private MusicBrainzClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        Sleeper sleeper = millis -> {
            sleeps.add(millis);
            nanos.addAndGet(millis * 1_000_000L);
        };
        client = new MusicBrainzClient(HttpClient.newHttpClient(), sleeper, nanos::get,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/ws/2/artist/", "TestBot/1.0");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(int... statuses) {
        server.createContext("/ws/2/artist/", exchange -> {
            int n = requests.getAndIncrement();
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            accepts.add(exchange.getRequestHeaders().getFirst("Accept"));
            queries.add(exchange.getRequestURI().toString());
            int status = statuses[Math.min(n, statuses.length - 1)];
            if (status == 503 || status == 429) {
                exchange.getResponseHeaders().add("Retry-After", "5");
            }
            byte[] body = (status == 200 ? "{\"relations\":[]}" : "busy").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Test
    void sendsTheProjectUserAgentAndAsksForJsonUrlRelations() {
        respond(200);

        assertEquals("{\"relations\":[]}", client.get("abc-123"));
        assertEquals("TestBot/1.0", userAgents.get(0));
        assertEquals("application/json", accepts.get(0));
        assertEquals("/ws/2/artist/abc-123?inc=url-rels&fmt=json", queries.get(0));
    }

    @Test
    void pacingIsAtLeastOneSecond() {
        respond(200);

        client.get("a");
        nanos.addAndGet(100_000_000L); // 100 ms pass before the next call
        client.get("b");

        assertEquals(List.of(1000L), sleeps);
        nanos.addAndGet(5_000_000_000L); // long enough ago: no wait
        client.get("c");
        assertEquals(1, sleeps.size());
    }

    @Test
    void honoursRetryAfterOnServiceUnavailable() {
        respond(503, 200);

        assertEquals("{\"relations\":[]}", client.get("a"));
        assertEquals(2, requests.get());
        assertTrue(sleeps.contains(5000L), "slept " + sleeps);
    }

    @Test
    void givesUpAfterFourRetriesAndDoesNotRetryNotFound() {
        respond(503);
        assertThrows(WikiFetchException.class, () -> client.get("a"));
        assertEquals(5, requests.get());
    }

    @Test
    void doesNotRetryNotFound() {
        respond(404);

        WikiFetchException e = assertThrows(WikiFetchException.class, () -> client.get("a"));
        assertEquals(1, requests.get());
        assertEquals(java.util.OptionalInt.of(404), e.httpStatus());
    }

    @Test
    void retriesOn429WithRetryAfter() {
        respond(429, 200);

        assertEquals("{\"relations\":[]}", client.get("a"));
        assertEquals(2, requests.get());
        assertTrue(sleeps.contains(5000L), "slept " + sleeps);
    }
}
