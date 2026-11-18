package com.nashi.wiki;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers like the Wikimedia APIs from the recorded fixtures, for tests that run the real {@link WikiCollector}
 * (a smaller cousin of the world in {@code WikiCollectorTest}). Occupation trees are just their root, the state honour
 * trees are recorded, people queries list the musicians given, the ensemble query the bands given.
 */
final class FakeWikimedia implements WikiHttp {

    private static final String SPARQL = "https://query.wikidata.org/sparql";
    private static final Pattern TREE_ROOT = Pattern.compile("\\?occ wdt:P279\\* wd:(Q\\d+)\\.");
    private static final Map<String, String> HONOUR_TREES = Map.of(
            StateHonours.ORDER, "sparql-honours-order.json",
            StateHonours.MEDAL, "sparql-honours-medal.json",
            StateHonours.STATE_DECORATION, "sparql-honours-state-decoration.json",
            StateHonours.HONORARY_TITLE, "sparql-honours-title.json",
            StateHonours.DEPARTMENTAL, "sparql-honours-departmental.json");
    /** Every recorded person and band; the artists fixture wins for Okean Elzy. */
    private static final String PEOPLE = merged("entities-kinds.json", "entities-awarded.json", "entities-artists.json");

    final List<String> sparqlQueries = new ArrayList<>();
    final List<String> urls = new ArrayList<>();
    private final String musicians;
    private final String bands;

    /** @param musicians (qid, title) pairs the people queries answer; @param bands the same for the ensemble query */
    FakeWikimedia(List<String> musicians, List<String> bands) {
        this.musicians = rows(musicians);
        this.bands = rows(bands);
    }

    @Override
    public String get(String url) {
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        urls.add(decoded);
        if (decoded.startsWith(SPARQL)) {
            String query = decoded.substring(decoded.indexOf("query=") + "query=".length());
            sparqlQueries.add(query);
            if (isTree(query)) {
                String root = rootOf(query);
                return HONOUR_TREES.containsKey(root) ? Fixtures.read(HONOUR_TREES.get(root)) : tree(root);
            }
            if (query.contains("wd:Q482994")) {
                return Fixtures.read("sparql-albums.json");
            }
            return query.contains("wd:Q2088357") ? bands : musicians;
        }
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=labels|descriptions|claims|sitelinks")) {
            return onlyRequested(PEOPLE, decoded);
        }
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=claims&")) {
            return onlyRequested(Fixtures.read("entities-awards.json"), decoded);
        }
        if (decoded.contains("action=wbgetentities") && decoded.contains("props=labels|claims")) {
            return Fixtures.read("entities-places.json");
        }
        if (decoded.contains("action=wbgetentities")) {
            return Fixtures.read("entities-genres.json");
        }
        if (decoded.startsWith("https://uk.wikipedia.org/")) {
            return Fixtures.read("extracts.json");
        }
        if (decoded.startsWith("https://commons.wikimedia.org/")) {
            return Fixtures.read("imageinfo.json");
        }
        throw new AssertionError("unexpected " + decoded);
    }

    /** Roots of the occupation and honour trees that were asked for. */
    List<String> treeRoots() {
        return sparqlQueries.stream().filter(FakeWikimedia::isTree).map(FakeWikimedia::rootOf).toList();
    }

    static boolean isTree(String query) {
        return query.startsWith("SELECT DISTINCT ?occ ");
    }

    private static String rootOf(String query) {
        Matcher matcher = TREE_ROOT.matcher(query);
        if (!matcher.find()) {
            throw new AssertionError(query);
        }
        return matcher.group(1);
    }

    private static String tree(String root) {
        return "{\"head\":{\"vars\":[\"occ\"]},\"results\":{\"bindings\":[{\"occ\":{\"type\":\"uri\","
                + "\"value\":\"http://www.wikidata.org/entity/" + root + "\"}}]}}";
    }

    private static String rows(List<String> qidsAndTitles) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < qidsAndTitles.size(); i += 2) {
            rows.add("{\"item\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + qidsAndTitles.get(i)
                    + "\"},\"title\":{\"xml:lang\":\"uk\",\"type\":\"literal\",\"value\":\"" + qidsAndTitles.get(i + 1)
                    + "\"}}");
        }
        return "{\"head\":{\"vars\":[\"item\",\"title\"]},\"results\":{\"bindings\":[" + String.join(",", rows) + "]}}";
    }

    private static String merged(String... fixtures) {
        JsonObject entities = new JsonObject();
        for (String fixture : fixtures) {
            JsonParser.parseString(Fixtures.read(fixture)).getAsJsonObject().getAsJsonObject("entities").entrySet()
                    .forEach(entry -> entities.add(entry.getKey(), entry.getValue()));
        }
        JsonObject root = new JsonObject();
        root.add("entities", entities);
        return root.toString();
    }

    private static String onlyRequested(String json, String decodedUrl) {
        Set<String> ids = Set.copyOf(Arrays.asList(decodedUrl.replaceAll(".*[?&]ids=([^&]*).*", "$1").split("\\|")));
        JsonObject all = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("entities");
        JsonObject entities = new JsonObject();
        ids.stream().filter(all::has).forEach(id -> entities.add(id, all.get(id)));
        JsonObject root = new JsonObject();
        root.add("entities", entities);
        return root.toString();
    }
}
