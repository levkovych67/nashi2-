package com.nashi.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address. With {@code server.forward-headers-strategy=framework} {@code getRemoteAddr()} already
 * reflects {@code X-Forwarded-For}; only our nginx can set it, as the backend listens on the internal network only.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
