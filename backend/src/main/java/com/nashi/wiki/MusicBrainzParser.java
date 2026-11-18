package com.nashi.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.wiki.WikiLink.Platform;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Music platform links out of the {@code relations[].url.resource} of a MusicBrainz artist (inc=url-rels). */
public final class MusicBrainzParser {

    private MusicBrainzParser() {
    }

    /** Links on known music hosts, query and fragment stripped, a YouTube channel first among YouTube links. */
    public static List<WikiLink> links(String json) {
        List<WikiLink> links = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("relations")
                || !root.getAsJsonObject().get("relations").isJsonArray()) {
            return links;
        }
        JsonArray relations = root.getAsJsonObject().getAsJsonArray("relations");
        for (JsonElement relation : relations) {
            if (!relation.isJsonObject()) {
                continue;
            }
            JsonObject url = relation.getAsJsonObject().has("url") && relation.getAsJsonObject().get("url").isJsonObject()
                    ? relation.getAsJsonObject().getAsJsonObject("url") : null;
            if (url == null || !url.has("resource") || !url.get("resource").isJsonPrimitive()) {
                continue;
            }
            WikiLink link = classify(url.get("resource").getAsString());
            if (link != null && links.stream().noneMatch(l -> l.getUrl().equals(link.getUrl()))) {
                links.add(link);
            }
        }
        // a channel id is the only YouTube form the player can use, so it goes before handles and names
        links.sort((a, b) -> Boolean.compare(isChannel(b), isChannel(a)));
        return links;
    }

    private static boolean isChannel(WikiLink link) {
        return link.getPlatform() == Platform.YOUTUBE && link.getUrl().contains("/channel/UC");
    }

    private static WikiLink classify(String resource) {
        URI uri;
        try {
            uri = URI.create(resource.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (host.isEmpty() || path.isEmpty()) {
            return null;
        }
        String bare = host.startsWith("www.") ? host.substring(4) : host;

        switch (bare) {
            case "youtube.com", "m.youtube.com" -> {
                return path.length() > 1 ? link(Platform.YOUTUBE, "https://www.youtube.com" + path) : null;
            }
            case "music.youtube.com" -> {
                return path.startsWith("/channel/UC") ? link(Platform.YOUTUBE, "https://www.youtube.com" + path) : null;
            }
            case "open.spotify.com" -> {
                return path.startsWith("/artist/") ? link(Platform.SPOTIFY, "https://open.spotify.com" + path) : null;
            }
            case "music.apple.com", "itunes.apple.com" -> {
                return path.contains("/artist/") ? link(Platform.APPLE_MUSIC, "https://" + bare + path) : null;
            }
            case "deezer.com" -> {
                return path.contains("/artist/") ? link(Platform.DEEZER, "https://www.deezer.com" + path) : null;
            }
            case "soundcloud.com" -> {
                return path.length() > 1 ? link(Platform.SOUNDCLOUD, "https://soundcloud.com" + path) : null;
            }
            default -> {
                if (bare.endsWith(".bandcamp.com")) {
                    return link(Platform.BANDCAMP, "https://" + bare + path);
                }
                return null;
            }
        }
    }

    private static WikiLink link(Platform platform, String url) {
        return WikiLink.builder().platform(platform).url(url).build();
    }
}
