package com.nashi.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads MediaWiki API responses (formatversion=2): article intros, Commons image info and category members. */
public final class MediaWikiParser {

    /** An ISO date in brackets right after text, with no space: the template's hidden copy of the date. */
    private static final Pattern GLUED_ISO_DATE = Pattern.compile("(?<=\\S)\\(\\d{4}-\\d{2}-\\d{2}\\)");
    private static final Pattern SPACE_BEFORE_COMMA = Pattern.compile("[ \\t\\u00A0]+,");

    private MediaWikiParser() {
    }

    /** Intro text keyed by article title, also by every title that was normalized or redirected to it. */
    public static Map<String, String> parseIntros(String json) {
        JsonObject query = query(json);
        Map<String, String> byTitle = new LinkedHashMap<>();
        if (query == null) {
            return byTitle;
        }
        if (query.has("pages")) {
            for (JsonElement element : query.getAsJsonArray("pages")) {
                JsonObject page = element.getAsJsonObject();
                if (page.has("extract") && !page.get("extract").getAsString().isBlank()) {
                    byTitle.put(page.get("title").getAsString(), cleanIntro(page.get("extract").getAsString()));
                }
            }
        }
        addAliases(query, byTitle);
        return byTitle;
    }

    /**
     * Cleans an intro extract:
     * <ul>
     *   <li>stress marks: Wikipedia marks stress with a combining acute accent (Мико́ла); it would break search and
     *   reads oddly;</li>
     *   <li>the hidden microformat date of a date template, which the extract keeps glued to the visible date:
     *   "нар. 14 травня 1975(1975-05-14) , Київ" becomes "нар. 14 травня 1975, Київ".</li>
     * </ul>
     */
    static String cleanIntro(String text) {
        String clean = text.replace("\u0301", "");
        clean = GLUED_ISO_DATE.matcher(clean).replaceAll("");
        clean = SPACE_BEFORE_COMMA.matcher(clean).replaceAll(",");
        return clean.trim();
    }

    /** Cuts long text at the last sentence that fits, or at a word, and marks the cut with "…". */
    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        for (int i = max - 1; i >= max / 2; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1))) {
                return text.substring(0, i + 1) + "…";
            }
        }
        int space = text.lastIndexOf(' ', max);
        int end = space > 0 ? space : max;
        return text.substring(0, end).trim() + "…";
    }

    /** Photo credits keyed by file name without the "File:" prefix. */
    public static Map<String, WikiImageCredit> parseImageCredits(String json) {
        JsonObject query = query(json);
        Map<String, WikiImageCredit> credits = new LinkedHashMap<>();
        if (query == null || !query.has("pages")) {
            return credits;
        }
        for (JsonElement element : query.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            JsonObject info = firstImageInfo(page);
            if (info == null) {
                continue;
            }
            JsonObject meta = info.has("extmetadata") ? info.getAsJsonObject("extmetadata") : new JsonObject();
            credits.put(fileName(page.get("title").getAsString()), WikiImageCredit.builder()
                    .author(stripHtml(metaValue(meta, "Artist")))
                    .license(blankToNull(metaValue(meta, "LicenseShortName")))
                    .licenseUrl(blankToNull(metaValue(meta, "LicenseUrl")))
                    .filePageUrl(info.has("descriptionurl") ? info.get("descriptionurl").getAsString() : null)
                    .build());
        }
        addFileAliases(query, credits);
        return credits;
    }

    /** Thumbnail URL (or the original when no thumbnail was made) keyed by file name without "File:". */
    public static Map<String, String> parseThumbUrls(String json) {
        JsonObject query = query(json);
        Map<String, String> urls = new LinkedHashMap<>();
        if (query == null || !query.has("pages")) {
            return urls;
        }
        for (JsonElement element : query.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            JsonObject info = firstImageInfo(page);
            if (info == null) {
                continue;
            }
            String url = info.has("thumburl") ? info.get("thumburl").getAsString()
                    : info.has("url") ? info.get("url").getAsString() : null;
            if (url != null) {
                // Commons appends tracking parameters (?utm_source=…); the image works without them.
                int params = url.indexOf('?');
                urls.put(fileName(page.get("title").getAsString()), params >= 0 ? url.substring(0, params) : url);
            }
        }
        addFileAliases(query, urls);
        return urls;
    }

    private static JsonObject query(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.has("query") ? root.getAsJsonObject("query") : null;
    }

    private static JsonObject firstImageInfo(JsonObject page) {
        if (!page.has("imageinfo") || page.getAsJsonArray("imageinfo").isEmpty()) {
            return null;
        }
        return page.getAsJsonArray("imageinfo").get(0).getAsJsonObject();
    }

    private static <T> void addAliases(JsonObject query, Map<String, T> byTitle) {
        Map<String, String> aliases = new HashMap<>();
        for (String kind : new String[]{"normalized", "redirects"}) {
            if (query.has(kind)) {
                for (JsonElement element : query.getAsJsonArray(kind)) {
                    JsonObject pair = element.getAsJsonObject();
                    aliases.put(pair.get("from").getAsString(), pair.get("to").getAsString());
                }
            }
        }
        for (String from : aliases.keySet()) {
            String to = from;
            for (int step = 0; step < 5 && aliases.containsKey(to); step++) {
                to = aliases.get(to);
            }
            T value = byTitle.get(to);
            if (value != null) {
                byTitle.putIfAbsent(from, value);
            }
        }
    }

    private static <T> void addFileAliases(JsonObject query, Map<String, T> byFile) {
        if (!query.has("normalized")) {
            return;
        }
        for (JsonElement element : query.getAsJsonArray("normalized")) {
            JsonObject pair = element.getAsJsonObject();
            T value = byFile.get(fileName(pair.get("to").getAsString()));
            if (value != null) {
                byFile.putIfAbsent(fileName(pair.get("from").getAsString()), value);
            }
        }
    }

    static String fileName(String title) {
        int colon = title.indexOf(':');
        return colon >= 0 ? title.substring(colon + 1) : title;
    }

    private static String metaValue(JsonObject meta, String key) {
        if (!meta.has(key)) {
            return null;
        }
        JsonObject field = meta.getAsJsonObject(key);
        return field.has("value") ? field.get("value").getAsString() : null;
    }

    static String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        String text = html.replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        return blankToNull(text);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
