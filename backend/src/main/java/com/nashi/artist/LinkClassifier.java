package com.nashi.artist;

import java.net.URI;
import java.util.Locale;

/**
 * Detects the platform of a link by its host (a domain matches itself and its subdomains). Anything that is not an
 * {@code http(s)} URL with a host is {@code OTHER}; an unknown host is {@code WEBSITE}.
 */
public final class LinkClassifier {

    private LinkClassifier() {
    }

    public static LinkPlatform classify(String url) {
        String host = hostOf(url);
        if (host == null) {
            return LinkPlatform.OTHER;
        }
        // music.youtube.com must be tested before youtube.com
        if (is(host, "music.youtube.com")) {
            return LinkPlatform.YOUTUBE_MUSIC;
        }
        if (is(host, "spotify.com", "spotify.link")) {
            return LinkPlatform.SPOTIFY;
        }
        if (is(host, "music.apple.com")) {
            return LinkPlatform.APPLE_MUSIC;
        }
        if (is(host, "deezer.com")) {
            return LinkPlatform.DEEZER;
        }
        if (is(host, "soundcloud.com")) {
            return LinkPlatform.SOUNDCLOUD;
        }
        if (is(host, "bandcamp.com")) {
            return LinkPlatform.BANDCAMP;
        }
        if (is(host, "youtube.com", "youtu.be")) {
            return LinkPlatform.YOUTUBE;
        }
        if (is(host, "wikipedia.org")) {
            return LinkPlatform.WIKIPEDIA;
        }
        if (is(host, "instagram.com")) {
            return LinkPlatform.INSTAGRAM;
        }
        if (is(host, "facebook.com", "fb.com")) {
            return LinkPlatform.FACEBOOK;
        }
        if (is(host, "tiktok.com")) {
            return LinkPlatform.TIKTOK;
        }
        if (is(host, "t.me", "telegram.me")) {
            return LinkPlatform.TELEGRAM;
        }
        if (is(host, "x.com", "twitter.com")) {
            return LinkPlatform.X;
        }
        return LinkPlatform.WEBSITE;
    }

    /**
     * True for an absolute {@code http(s)} URL with a host: the only links the site shows (submissions validate the
     * same rule; the V1 migration drops anything else).
     */
    public static boolean isAbsoluteHttp(String url) {
        return hostOf(url) != null;
    }

    private static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            String host = uri.getHost();
            return host == null || host.isBlank() ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean is(String host, String... domains) {
        for (String d : domains) {
            if (host.equals(d) || host.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }
}
