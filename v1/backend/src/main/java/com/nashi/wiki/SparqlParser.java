package com.nashi.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reads SPARQL JSON results. Malformed or cut-off JSON throws, so a caller can tell it from an empty answer. */
public final class SparqlParser {

    private static final Pattern QID = Pattern.compile("Q\\d+");
    private static final String FILE_PATH = "Special:FilePath/";

    private SparqlParser() {
    }

    /** Rows that bind {@code ?item} (entity URI) and {@code ?title} (uk.wikipedia title). */
    public static List<WikiCandidate> parseCandidates(String json, CandidateGroup group) {
        List<WikiCandidate> candidates = new ArrayList<>();
        for (JsonElement element : bindings(json)) {
            JsonObject binding = element.getAsJsonObject();
            if (!binding.has("item") || !binding.has("title")) {
                continue;
            }
            String uri = binding.getAsJsonObject("item").get("value").getAsString();
            String qid = uri.substring(uri.lastIndexOf('/') + 1);
            candidates.add(new WikiCandidate(qid, binding.getAsJsonObject("title").get("value").getAsString(), group));
        }
        return candidates;
    }

    /** The QIDs bound to {@code var}, in answer order; rows without it, or binding something else, are skipped. */
    public static List<String> parseIds(String json, String var) {
        List<String> ids = new ArrayList<>();
        for (JsonElement element : bindings(json)) {
            JsonObject binding = element.getAsJsonObject();
            if (!binding.has(var)) {
                continue;
            }
            String uri = binding.getAsJsonObject(var).get("value").getAsString();
            String id = uri.substring(uri.lastIndexOf('/') + 1);
            if (QID.matcher(id).matches()) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Rows of {@link WikiQueries#visualWorks} and {@link WikiQueries#writtenWorks}. The label service answers with the
     * work's QID when it has no label in uk or en; that counts as no title.
     */
    public static List<WorkRow> parseWorks(String json) {
        List<WorkRow> rows = new ArrayList<>();
        for (JsonElement element : bindings(json)) {
            JsonObject binding = element.getAsJsonObject();
            String creator = qid(binding, "creator");
            String work = qid(binding, "work");
            if (creator == null || work == null) {
                continue;
            }
            String title = value(binding, "title");
            if (title != null && (title.isBlank() || title.equals(work))) {
                title = null;
            }
            rows.add(new WorkRow(creator, work, title, integer(value(binding, "year")),
                    commonsFile(value(binding, "image")), value(binding, "ukTitle"),
                    value(binding, "wikisourceTitle"), "true".equals(value(binding, "notable"))));
        }
        return rows;
    }

    private static String value(JsonObject binding, String var) {
        return binding.has(var) ? binding.getAsJsonObject(var).get("value").getAsString() : null;
    }

    private static String qid(JsonObject binding, String var) {
        String uri = value(binding, var);
        if (uri == null) {
            return null;
        }
        String id = uri.substring(uri.lastIndexOf('/') + 1);
        return QID.matcher(id).matches() ? id : null;
    }

    private static Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** P18 comes as {@code http://commons.wikimedia.org/wiki/Special:FilePath/<percent-encoded file name>}. */
    private static String commonsFile(String uri) {
        if (uri == null || !uri.contains(FILE_PATH)) {
            return null;
        }
        String encoded = uri.substring(uri.indexOf(FILE_PATH) + FILE_PATH.length());
        // A "+" in a URI path is a plus sign, not a space.
        return URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static JsonArray bindings(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return root.getAsJsonObject("results").getAsJsonArray("bindings");
    }
}
