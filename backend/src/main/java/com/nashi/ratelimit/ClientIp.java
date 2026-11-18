package com.nashi.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address. With {@code server.forward-headers-strategy=framework} {@code getRemoteAddr()} already
 * reflects {@code X-Forwarded-For}. Trust assumption: the edge nginx overwrites that header with the client address
 * ({@code proxy_set_header X-Forwarded-For $remote_addr}) and the backend listens on the internal network only, so a
 * client cannot choose its own value.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
