package com.nashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    private final CorsFilter filter = CorsConfig.corsFilter(List.of(" https://nashi.example ", "http://localhost:5173"));

    @Test
    void allowsConfiguredOriginsWithoutCredentials() throws Exception {
        MockHttpServletResponse allowed = send(filter, request("GET", "https://nashi.example"));
        assertEquals(200, allowed.getStatus());
        assertEquals("https://nashi.example", allowed.getHeader("Access-Control-Allow-Origin"));
        assertNull(allowed.getHeader("Access-Control-Allow-Credentials"));

        assertEquals("http://localhost:5173",
                send(filter, request("GET", "http://localhost:5173")).getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void refusesOtherOrigins() throws Exception {
        MockHttpServletResponse other = send(filter, request("GET", "https://evil.example"));

        assertEquals(403, other.getStatus());
        assertNull(other.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void exposesRetryAfterAndEtag() throws Exception {
        String exposed = send(filter, request("GET", "https://nashi.example")).getHeader("Access-Control-Expose-Headers");

        assertTrue(exposed.contains("Retry-After"), exposed);
        assertTrue(exposed.contains("ETag"), exposed);
    }

    @Test
    void preflightAllowsGetAndPostOnly() throws Exception {
        MockHttpServletResponse post = send(filter, preflight("POST"));
        assertEquals(200, post.getStatus());
        assertTrue(post.getHeader("Access-Control-Allow-Methods").contains("POST"));
        assertEquals("https://nashi.example", post.getHeader("Access-Control-Allow-Origin"));

        assertEquals(403, send(filter, preflight("DELETE")).getStatus());
    }

    @Test
    void noConfiguredOriginsRefusesEveryCrossOriginRequest() throws Exception {
        assertEquals(403, send(CorsConfig.corsFilter(List.of()), request("GET", "https://nashi.example")).getStatus());
    }

    @Test
    void readsOriginsFromProperties() throws Exception {
        NashiProperties properties = new NashiProperties(new NashiProperties.CorsProps(List.of("https://nashi.example")),
                null, null, null, null, null, null);

        MockHttpServletResponse response = send(new CorsConfig(properties).corsFilter(), request("GET", "https://nashi.example"));

        assertEquals("https://nashi.example", response.getHeader("Access-Control-Allow-Origin"));
    }

    private static MockHttpServletRequest request(String method, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v2/map/pins");
        request.setScheme("https");
        request.setServerName("api.nashi.example");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        return request;
    }

    private static MockHttpServletRequest preflight(String method) {
        MockHttpServletRequest request = request("OPTIONS", "https://nashi.example");
        request.addHeader("Access-Control-Request-Method", method);
        request.addHeader("Access-Control-Request-Headers", "Content-Type");
        return request;
    }

    private static MockHttpServletResponse send(CorsFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
