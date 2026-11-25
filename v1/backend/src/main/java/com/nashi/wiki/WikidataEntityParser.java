package com.nashi.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the response of {@code wbgetentities} (props labels|claims|sitelinks). */
public final class WikidataEntityParser {

    static final List<String> SOCIAL_PROPERTIES = List.of("P856", "P2003", "P2013", "P2397", "P1902", "P3040",
            "P2002", "P7085", "P3789", "P2850", "P2722", "P3283", "P1953", "P434", "P3192", "P2963", "P648", "P2042",
            "P4613", "P13437", "P6002");

    private WikidataEntityParser() {
    }

    public static Map<String, WikiEntity> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, WikiEntity> result = new LinkedHashMap<>();
        if (!root.has("entities")) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("entities").entrySet()) {
            JsonObject e = entry.getValue().getAsJsonObject();
            if (e.has("missing") || !e.has("id")) {
                continue;
            }
            WikiEntity entity = toEntity(e);
            result.put(entity.qid(), entity);
        }
        return result;
    }

    private static WikiEntity toEntity(JsonObject e) {
        JsonObject claims = e.has("claims") && e.get("claims").isJsonObject() ? e.getAsJsonObject("claims") : new JsonObject();

        Map<String, String> social = new LinkedHashMap<>();
        for (String property : SOCIAL_PROPERTIES) {
            String value = firstString(best(claims, property));
            if (value != null && !value.isBlank()) {
                social.put(property, value.trim());
            }
        }

        JsonObject coordinates = firstValueObject(best(claims, "P625"));

        return new WikiEntity(
                e.get("id").getAsString(),
                label(e, "uk"),
                label(e, "en"),
                ukTitle(e),
                new LinkedHashSet<>(itemIds(ordered(claims, "P31"))),
                new LinkedHashSet<>(itemIds(ordered(claims, "P27"))),
                itemIds(ordered(claims, "P19")),
                itemIds(ordered(claims, "P740")),
                itemIds(ordered(claims, "P551")),
                itemIds(ordered(claims, "P937")),
                firstYear(best(claims, "P571")),
                firstYear(best(claims, "P576")),
                firstYear(best(claims, "P2031")),
                firstYear(best(claims, "P2032")),
                firstString(best(claims, "P18")),
                itemIds(ordered(claims, "P136")),
                social,
                coordinates != null && coordinates.has("latitude") ? coordinates.get("latitude").getAsDouble() : null,
                coordinates != null && coordinates.has("longitude") ? coordinates.get("longitude").getAsDouble() : null,
                itemIds(current(claims, "P131")),
                itemIds(ordered(claims, "P106")),
                date(best(claims, "P569")),
                date(best(claims, "P570")),
                itemIds(ordered(claims, "P135")),
                itemIds(ordered(claims, "P800")),
                trimmed(firstString(best(claims, "P373"))),
                sitelinkTitle(e, "ukwikisource"),
                itemIds(ordered(claims, "P166")),
                itemIds(best(claims, "P17")),
                itemIds(ordered(claims, "P361")),
                itemIds(best(claims, "P1027")));
    }

    private static String label(JsonObject e, String language) {
        if (!e.has("labels")) {
            return null;
        }
        JsonObject labels = e.getAsJsonObject("labels");
        return labels.has(language) ? labels.getAsJsonObject(language).get("value").getAsString() : null;
    }

    private static String ukTitle(JsonObject e) {
        if (!e.has("sitelinks")) {
            return null;
        }
        JsonObject sitelinks = e.getAsJsonObject("sitelinks");
        return sitelinkTitle(e, "ukwiki");
    }

    private static String sitelinkTitle(JsonObject e, String site) {
        if (!e.has("sitelinks") || !e.get("sitelinks").isJsonObject()) {
            return null;
        }
        JsonObject sitelinks = e.getAsJsonObject("sitelinks");
        return sitelinks.has(site) ? sitelinks.getAsJsonObject(site).get("title").getAsString() : null;
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static WikiDate date(List<JsonObject> claims) {
        JsonObject time = firstValueObject(claims);
        if (time == null || !time.has("time") || !time.has("precision")) {
            return null;
        }
        return WikiDate.of(time.get("time").getAsString(), time.get("precision").getAsInt(),
                time.has("calendarmodel") ? time.get("calendarmodel").getAsString() : null);
    }

    /** Non-deprecated statements, preferred first. */
    private static List<JsonObject> ordered(JsonObject claims, String property) {
        List<JsonObject> preferred = new ArrayList<>();
        List<JsonObject> normal = new ArrayList<>();
        if (claims.has(property)) {
            for (JsonElement element : claims.getAsJsonArray(property)) {
                JsonObject claim = element.getAsJsonObject();
                String rank = claim.has("rank") ? claim.get("rank").getAsString() : "normal";
                if ("preferred".equals(rank)) {
                    preferred.add(claim);
                } else if (!"deprecated".equals(rank)) {
                    normal.add(claim);
                }
            }
        }
        preferred.addAll(normal);
        return preferred;
    }

    /** Wikidata's "best" statements: the preferred ones if any, else the normal ones. */
    private static List<JsonObject> best(JsonObject claims, String property) {
        List<JsonObject> ordered = ordered(claims, property);
        List<JsonObject> preferred = ordered.stream().filter(c -> "preferred".equals(rank(c))).toList();
        return preferred.isEmpty() ? ordered : preferred;
    }

    /** Best statements that have no end time (P582), so historical units are not followed. */
    private static List<JsonObject> current(JsonObject claims, String property) {
        List<JsonObject> open = ordered(claims, property).stream()
                .filter(c -> !(c.has("qualifiers") && c.getAsJsonObject("qualifiers").has("P582")))
                .toList();
        List<JsonObject> preferred = open.stream().filter(c -> "preferred".equals(rank(c))).toList();
        return preferred.isEmpty() ? open : preferred;
    }

    private static String rank(JsonObject claim) {
        return claim.has("rank") ? claim.get("rank").getAsString() : "normal";
    }

    private static JsonElement value(JsonObject claim) {
        JsonObject snak = claim.getAsJsonObject("mainsnak");
        if (snak == null || !snak.has("datavalue")) {
            return null;
        }
        return snak.getAsJsonObject("datavalue").get("value");
    }

    private static List<String> itemIds(List<JsonObject> claims) {
        List<String> ids = new ArrayList<>();
        for (JsonObject claim : claims) {
            JsonElement value = value(claim);
            if (value != null && value.isJsonObject() && value.getAsJsonObject().has("id")) {
                String id = value.getAsJsonObject().get("id").getAsString();
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static String firstString(List<JsonObject> claims) {
        for (JsonObject claim : claims) {
            JsonElement value = value(claim);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsString();
            }
        }
        return null;
    }

    private static JsonObject firstValueObject(List<JsonObject> claims) {
        for (JsonObject claim : claims) {
            JsonElement value = value(claim);
            if (value != null && value.isJsonObject()) {
                return value.getAsJsonObject();
            }
        }
        return null;
    }

    /** "+1994-10-12T00:00:00Z" → 1994. */
    private static Integer firstYear(List<JsonObject> claims) {
        JsonObject time = firstValueObject(claims);
        if (time == null || !time.has("time")) {
            return null;
        }
        String text = time.get("time").getAsString();
        boolean negative = text.startsWith("-");
        String digits = text.replaceFirst("^[+-]", "");
        int dash = digits.indexOf('-');
        if (dash <= 0) {
            return null;
        }
        try {
            int year = Integer.parseInt(digits.substring(0, dash));
            return negative ? -year : year;
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
