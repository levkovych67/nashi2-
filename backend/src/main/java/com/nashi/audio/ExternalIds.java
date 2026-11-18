package com.nashi.audio;

import com.nashi.artist.Link;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Artist ids on streaming services, read from a musician's links. */
public final class ExternalIds {

    private static final Pattern DEEZER = Pattern.compile("deezer\\.com/(?:[a-z]{2}/)?artist/(\\d+)");
    private static final Pattern APPLE = Pattern.compile(
            "(?:music\\.apple\\.com/(?:[a-z]{2}/)?artist/(?:[^/]+/)?(\\d+)|itunes\\.apple\\.com/.*?/id(\\d+))");

    private ExternalIds() {
    }

    public static Optional<String> deezerArtistId(List<Link> links) {
        return first(links, DEEZER);
    }

    public static Optional<String> appleArtistId(List<Link> links) {
        return first(links, APPLE);
    }

    private static Optional<String> first(List<Link> links, Pattern pattern) {
        if (links == null) {
            return Optional.empty();
        }
        for (Link link : links) {
            if (link == null || link.url() == null) {
                continue;
            }
            var m = pattern.matcher(link.url());
            if (m.find()) {
                for (int g = 1; g <= m.groupCount(); g++) {
                    if (m.group(g) != null) {
                        return Optional.of(m.group(g));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
