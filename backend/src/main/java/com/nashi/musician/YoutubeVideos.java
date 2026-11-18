package com.nashi.musician;

import com.nashi.artist.Link;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.Video;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Derives music videos from the artist's stored YOUTUBE links (not YOUTUBE_MUSIC). */
final class YoutubeVideos {

    private static final int MAX_VIDEOS = 6;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private YoutubeVideos() {
    }

    static List<Video> fromLinks(List<Link> links, String title) {
        if (links == null) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Link link : links) {
            if (link == null || link.platform() != LinkPlatform.YOUTUBE) {
                continue;
            }
            idOf(link.url()).ifPresent(ids::add);
            if (ids.size() >= MAX_VIDEOS) {
                break;
            }
        }
        return ids.stream().map(id -> new Video(id, title)).toList();
    }

    private static Optional<String> idOf(String url) {
        if (url == null) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        String candidate = null;
        if (host.equals("youtu.be")) {
            candidate = firstSegment(path, 0);
        } else if (isYoutubeHost(host)) {
            if (path.equals("/watch")) {
                candidate = queryParam(uri.getRawQuery(), "v");
            } else if (path.startsWith("/shorts/") || path.startsWith("/embed/")) {
                candidate = firstSegment(path, 1);
            }
        }
        return candidate != null && ID.matcher(candidate).matches()
                ? Optional.of(candidate)
                : Optional.empty();
    }

    private static boolean isYoutubeHost(String host) {
        return host.equals("youtube.com") || host.endsWith(".youtube.com");
    }

    /** Segment at index {@code skip} of the path, e.g. "/shorts/ID" with skip 1 gives ID. */
    private static String firstSegment(String path, int skip) {
        String[] parts = path.split("/");
        List<String> segments = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) {
                segments.add(p);
            }
        }
        return segments.size() > skip ? segments.get(skip) : null;
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
