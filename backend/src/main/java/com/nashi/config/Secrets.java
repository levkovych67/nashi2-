package com.nashi.config;

import java.util.regex.Pattern;

/** Masks secrets in the {@code toString} of configuration records, which can reach logs and failure reports. */
public final class Secrets {

    private static final String MASK = "***";
    /** The user-info part of {@code scheme://user:password@host...}. */
    private static final Pattern URI_CREDENTIALS = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://)[^@/]*@");

    private Secrets() {
    }

    /** {@code ***} for a set value, {@code null} when unset (so it still shows whether the secret is configured). */
    public static String mask(String secret) {
        return secret == null ? "null" : MASK;
    }

    /** The URI with any {@code user:password@} replaced by {@code ***@}. */
    public static String maskUriCredentials(String uri) {
        return uri == null ? "null" : URI_CREDENTIALS.matcher(uri).replaceFirst("$1" + MASK + "@");
    }
}
