package com.nashi.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import com.nashi.wiki.PoliteWikiHttp;
import com.nashi.wiki.WikiHttp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recordings Wikidata lists for the musician (property P51, "audio") that Wikimedia Commons can play as MP3, so they
 * work on an iPhone: an {@code audio/mpeg} original, or an MP3 derivative of an Ogg/WAV/FLAC original. Files with
 * neither are dropped. Commons category audio is not read yet.
 */
@Component
public class CommonsAudioProvider implements AudioProvider {

    static final int MAX_FILES = 5;
    private static final int MAX_AUTHOR_LENGTH = 120;

    private final WikiHttp http;

    @Autowired
    public CommonsAudioProvider(PoliteWikiHttp http) {
        this((WikiHttp) http);
    }

    CommonsAudioProvider(WikiHttp http) {
        this.http = http;
    }

    @Override
    public AudioSource source() {
        return AudioSource.COMMONS;
    }

    @Override
    public List<AudioClip> find(Artist artist) {
        String qid = artist.getWikidataId();
        if (qid == null || qid.isBlank()) {
            return List.of();
        }
        List<String> files = audioFiles(qid);
        if (files.isEmpty()) {
            return List.of();
        }
        Map<String, JsonObject> pages = commonsPages(files);
        List<AudioClip> clips = new ArrayList<>();
        for (String file : files) {
            JsonObject page = pages.get(file);
            if (page == null) {
                continue;
            }
            toClip(file, page).ifPresent(clips::add);
            if (clips.size() == MAX_FILES) {
                break;
            }
        }
        return clips;
    }

    private List<String> audioFiles(String qid) {
        String url = "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P51&entity="
                + URLEncoder.encode(qid, StandardCharsets.UTF_8);
        JsonObject claims = Json.object(JsonParser.parseString(http.get(url)).getAsJsonObject(), "claims")
                .orElse(new JsonObject());
        List<String> files = new ArrayList<>();
        JsonElement p51 = claims.get("P51");
        if (p51 == null || !p51.isJsonArray()) {
            return files;
        }
        for (JsonElement statement : p51.getAsJsonArray()) {
            Optional<String> name = Json.object(statement.getAsJsonObject(), "mainsnak")
                    .flatMap(s -> Json.object(s, "datavalue")).flatMap(v -> Json.string(v, "value"));
            name.map(n -> n.replace('_', ' ')).filter(n -> !files.contains(n)).ifPresent(files::add);
        }
        return files;
    }

    /** One Commons request for all files; pages keyed by the file name without the "File:" prefix. */
    private Map<String, JsonObject> commonsPages(List<String> files) {
        String titles = String.join("|", files.stream().map(f -> "File:" + f).toList());
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                + "&prop=imageinfo%7Cvideoinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata"
                + "&viprop=url%7Csize%7Cmime%7Cderivatives&titles=" + URLEncoder.encode(titles, StandardCharsets.UTF_8);
        JsonObject query = Json.object(JsonParser.parseString(http.get(url)).getAsJsonObject(), "query")
                .orElse(new JsonObject());
        Map<String, JsonObject> pages = new HashMap<>();
        JsonElement array = query.get("pages");
        if (array != null && array.isJsonArray()) {
            for (JsonElement page : (JsonArray) array) {
                JsonObject object = page.getAsJsonObject();
                Json.string(object, "title").ifPresent(t -> pages.put(stripPrefix(t), object));
            }
        }
        return pages;
    }

    private static Optional<AudioClip> toClip(String file, JsonObject page) {
        Optional<JsonObject> image = Json.first(page, "imageinfo");
        Optional<JsonObject> video = Json.first(page, "videoinfo");
        Optional<String> playable = mp3Url(image, video);
        if (playable.isEmpty()) {
            return Optional.empty();
        }
        JsonObject meta = image.flatMap(i -> Json.object(i, "extmetadata")).orElse(new JsonObject());
        String author = Json.object(meta, "Artist").flatMap(a -> Json.string(a, "value")).map(CommonsAudioProvider::plain)
                .orElse("");
        String license = Json.object(meta, "LicenseShortName").flatMap(a -> Json.string(a, "value"))
                .map(CommonsAudioProvider::plain).orElse("");
        double duration = image.or(() -> video).map(i -> i.get("duration")).filter(e -> e != null && e.isJsonPrimitive())
                .map(JsonElement::getAsDouble).orElse(0d);
        String externalUrl = image.or(() -> video).flatMap(i -> Json.string(i, "descriptionurl")).orElse(null);
        return Optional.of(new AudioClip("cm_" + sha1Prefix(file), AudioSource.COMMONS, file, null, playable.get(),
                title(file), (int) Math.round(duration), attribution(author, license), externalUrl));
    }

    private static Optional<String> mp3Url(Optional<JsonObject> image, Optional<JsonObject> video) {
        Optional<JsonObject> original = image.or(() -> video);
        if (original.flatMap(o -> Json.string(o, "mime")).filter("audio/mpeg"::equals).isPresent()) {
            return original.flatMap(o -> Json.string(o, "url")).map(CommonsAudioProvider::withoutQuery);
        }
        JsonElement derivatives = video.map(v -> v.get("derivatives")).orElse(null);
        if (derivatives == null || !derivatives.isJsonArray()) {
            return Optional.empty();
        }
        for (JsonElement derivative : derivatives.getAsJsonArray()) {
            JsonObject d = derivative.getAsJsonObject();
            if (Json.string(d, "type").map(t -> t.startsWith("audio/mpeg")).orElse(false)) {
                return Json.string(d, "src").map(CommonsAudioProvider::withoutQuery);
            }
        }
        return Optional.empty();
    }

    static String attribution(String author, String license) {
        List<String> parts = new ArrayList<>();
        if (!author.isBlank()) {
            parts.add(author);
        }
        if (!license.isBlank()) {
            parts.add(license);
        }
        parts.add("Wikimedia Commons");
        return "Запис: " + String.join(", ", parts);
    }

    /** extmetadata values are HTML ({@code <a href=...>Name</a>}); keep the text, and cap a long description. */
    private static String plain(String html) {
        String text = html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > MAX_AUTHOR_LENGTH ? text.substring(0, MAX_AUTHOR_LENGTH - 1).trim() + "…" : text;
    }

    private static String title(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private static String stripPrefix(String title) {
        int colon = title.indexOf(':');
        return colon >= 0 ? title.substring(colon + 1) : title;
    }

    private static String withoutQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    static String sha1Prefix(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(name.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
