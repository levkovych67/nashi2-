package com.nashi.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address. With {@code server.forward-headers-strategy=native} Tomcat's {@code RemoteIpValve} has already
 * set {@code getRemoteAddr()} from {@code X-Forwarded-For}. Trust model: the valve ignores the RFC 7239
 * {@code Forwarded} header and walks {@code X-Forwarded-For} from the right, skipping internal proxies
 * ({@code server.tomcat.remoteip.internal-proxies}: private, loopback and link-local ranges), and takes the first
 * (rightmost) hop that is not one. nginx appends its {@code $remote_addr} ({@code $proxy_add_x_forwarded_for}) and
 * drops {@code Forwarded}, and the backend listens on the internal network only, so whatever a client writes into
 * those headers itself sits to the left of that hop and cannot choose the address.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
