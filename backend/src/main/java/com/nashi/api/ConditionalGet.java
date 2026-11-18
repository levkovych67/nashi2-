package com.nashi.api;

import org.springframework.http.ResponseEntity;

import java.util.function.Supplier;

/**
 * {@code ETag} + {@code Cache-Control: public, max-age=60} + {@code If-None-Match} to 304
 * ({@code docs/02-contracts.md} section 1). Weak comparison; the {@code *} and comma-separated list forms are handled.
 */
public final class ConditionalGet {

    static final String CACHE_CONTROL = "public, max-age=60";

    private ConditionalGet() {
    }

    /** 304 without a body when {@code ifNoneMatch} matches {@code "<version>"}; otherwise 200 with the body.
     *  Both carry the ETag and Cache-Control headers. */
    public static <T> ResponseEntity<T> respond(String version, String ifNoneMatch, Supplier<T> body) {
        String etag = "\"" + version + "\"";
        ResponseEntity.BodyBuilder builder = matches(etag, ifNoneMatch)
                ? ResponseEntity.status(304) : ResponseEntity.ok();
        builder.eTag(etag).header("Cache-Control", CACHE_CONTROL);
        return matches(etag, ifNoneMatch) ? builder.build() : builder.body(body.get());
    }

    static boolean matches(String etag, String ifNoneMatch) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.strip();
            if (c.equals("*")) {
                return true;
            }
            if (c.startsWith("W/")) {
                c = c.substring(2);
            }
            if (c.equals(etag)) {
                return true;
            }
        }
        return false;
    }

}
