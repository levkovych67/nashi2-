package com.nashi.place.seed;

import com.nashi.geo.Region;
import com.nashi.place.PlaceType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads WDQS JSON answers ({@code application/sparql-results+json}) of the place queries of
 * {@link PlaceSeedGenerator}. One place comes back as several rows (one per type, coordinate, area and population
 * value); they are folded into one {@link PlaceSeedRow}: the highest-ranked known type ({@link PlaceType} order),
 * the largest area, the population with the latest date ({@code ?popDate}; undated values count as oldest, the
 * larger wins a tie) and the southernmost-then-westernmost coordinate, so the result does not depend on row order.
 * Rows without a Ukrainian label, a coordinate on Earth or a known type are dropped.
 */
public final class PlaceSparqlParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ENTITY = "http://www.wikidata.org/entity/";
    /** WKT on Earth: other globes prefix the point with their IRI. */
    private static final Pattern POINT = Pattern.compile("^Point\\(\\s*(-?[0-9.Ee+-]+)\\s+(-?[0-9.Ee+-]+)\\s*\\)$");
    private static final BigDecimal SQUARE_METRES_PER_KM2 = BigDecimal.valueOf(1_000_000);

    private PlaceSparqlParser() {
    }

    /** @param region the region the query asked about; every row gets it */
    public static List<PlaceSeedRow> parse(String json, Region region) {
        Map<String, Folded> byQid = new LinkedHashMap<>();
        for (JsonNode b : bindings(json)) {
            String qid = qid(b.path("place"));
            JsonNode label = b.path("label");
            Optional<PlaceType> type = PlaceTypes.of(qid(b.path("type")));
            Optional<double[]> point = point(b.path("coord").path("value").asString(""));
            if (qid == null || !"uk".equals(label.path("xml:lang").asString(""))
                    || label.path("value").asString("").isBlank() || point.isEmpty() || type.isEmpty()) {
                continue;
            }
            byQid.computeIfAbsent(qid, q -> new Folded(q, label.path("value").asString().strip()))
                    .add(type.get(), point.get(), decimal(b.path("areaM2")), decimal(b.path("pop")),
                            b.path("popDate").path("value").asString(""));
        }
        List<PlaceSeedRow> rows = new ArrayList<>(byQid.size());
        byQid.values().forEach(f -> rows.add(f.row(region)));
        return rows;
    }

    /** The distinct QIDs bound to {@code variable}, in answer order. */
    public static List<String> parseQids(String json, String variable) {
        Set<String> qids = new LinkedHashSet<>();
        for (JsonNode b : bindings(json)) {
            String qid = qid(b.path(variable));
            if (qid != null) {
                qids.add(qid);
            }
        }
        return List.copyOf(qids);
    }

    private static JsonNode bindings(String json) {
        JsonNode results = JSON.readTree(json).path("results");
        if (!results.has("bindings")) {
            // WDQS sometimes ends a timed-out answer early with status 200; treat it like a failure, not as empty
            throw new TruncatedAnswerException();
        }
        return results.path("bindings");
    }

    private static String qid(JsonNode uri) {
        String value = uri.path("value").asString("");
        return value.startsWith(ENTITY) ? value.substring(ENTITY.length()) : null;
    }

    private static Optional<double[]> point(String wkt) {
        Matcher m = POINT.matcher(wkt.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            double lon = Double.parseDouble(m.group(1));
            double lat = Double.parseDouble(m.group(2));
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                return Optional.empty();
            }
            return Optional.of(new double[] {lat, lon});
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal decimal(JsonNode literal) {
        String value = literal.path("value").asString("");
        if (value.isBlank()) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(value.strip());
            return d.signum() > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** WDQS cut the answer off (a timeout after the first bytes were sent): ask a smaller question. */
    public static final class TruncatedAnswerException extends RuntimeException {
        TruncatedAnswerException() {
            super("SPARQL answer without results.bindings");
        }
    }

    private static final class Folded {
        private final String qid;
        private final String name;
        private PlaceType type;
        private double[] point;
        private BigDecimal areaM2;
        private BigDecimal population;
        private String populationDate = "";

        Folded(String qid, String name) {
            this.qid = qid;
            this.name = name;
        }

        void add(PlaceType t, double[] p, BigDecimal area, BigDecimal pop, String popDate) {
            if (type == null || t.ordinal() < type.ordinal()) {
                type = t;
            }
            if (point == null || p[0] < point[0] || (p[0] == point[0] && p[1] < point[1])) {
                point = p;
            }
            if (area != null && (areaM2 == null || area.compareTo(areaM2) > 0)) {
                areaM2 = area;
            }
            if (pop != null && (population == null || popDate.compareTo(populationDate) > 0
                    || (popDate.equals(populationDate) && pop.compareTo(population) > 0))) {
                population = pop;
                populationDate = popDate;
            }
        }

        PlaceSeedRow row(Region region) {
            Double km2 = areaM2 == null ? null
                    : areaM2.divide(SQUARE_METRES_PER_KM2).setScale(3, RoundingMode.HALF_UP).doubleValue();
            Long pop = population == null ? null : population.setScale(0, RoundingMode.HALF_UP).longValueExact();
            return new PlaceSeedRow(qid, name, type, region, point[0], point[1], km2 == null || km2 == 0 ? null : km2,
                    pop);
        }
    }
}
