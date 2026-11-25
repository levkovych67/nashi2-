package com.nashi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsConfigTest {

    @Test
    void originsReadFromProperty() throws Exception {
        CorsFilter filter = new CorsConfig(" https://nashi.example , http://localhost:5173 ").corsFilter();

        MockHttpServletResponse allowed = get(filter, "https://nashi.example");
        assertEquals(200, allowed.getStatus());
        assertEquals("https://nashi.example", allowed.getHeader("Access-Control-Allow-Origin"));
        assertNull(allowed.getHeader("Access-Control-Allow-Credentials"));

        assertEquals("http://localhost:5173", get(filter, "http://localhost:5173").getHeader("Access-Control-Allow-Origin"));

        MockHttpServletResponse other = get(filter, "https://evil.example");
        assertEquals(403, other.getStatus());
        assertNull(other.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void starAllowsAnyOriginWithoutCredentials() throws Exception {
        MockHttpServletResponse response = get(new CorsConfig("*").corsFilter(), "https://anywhere.example");

        assertEquals(200, response.getStatus());
        assertEquals("https://anywhere.example", response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    private static MockHttpServletResponse get(CorsFilter filter, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/map/pins");
        request.setScheme("https");
        request.setServerName("api.nashi.example");
        request.setServerPort(443);
        request.addHeader("Origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
