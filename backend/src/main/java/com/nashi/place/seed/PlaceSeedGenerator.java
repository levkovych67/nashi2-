package com.nashi.place.seed;

import com.nashi.geo.Region;
import com.nashi.place.PlaceType;
import com.nashi.wiki.PoliteWikiHttp;
import com.nashi.wiki.UkraineRegions;
import com.nashi.wiki.WikiFetchException;
import com.nashi.wiki.WikiHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Builds the places seed from Wikidata ({@code ./gradlew placesSeed}). A plain program: no Spring, no database.
 * <ul>
 *   <li>{@code --out <dir>}: asks WDQS for the settlements of every region (sequentially, through
 *   {@link PoliteWikiHttp}) and writes {@code <dir>/places-ua.json.gz} (one {@link PlaceSeedRow} per line, sorted by
 *   QID number) and {@code <dir>/places-ua.version} ({@code <date>+<content hash>}).</li>
 *   <li>{@code --types}: prints how many Ukrainian settlements Wikidata has per {@code P31} type (subclasses of human
 *   settlement {@code Q486972}); used to write {@link PlaceTypes}.</li>
 * </ul>
 * Each oblast (and Crimea) is one query over its {@code P131+} tree. When WDQS cannot answer it in time, the area is
 * split: its direct settlements in one query, then its sub-units (children that have children of their own) in
 * chunks, halving a chunk that fails and splitting a single sub-unit that fails again, down to
 * {@value #MAX_SPLIT_DEPTH} levels. Kyiv and Sevastopol are queried with {@link #CITY_PATH} below {@link #CITY_ROOTS}
 * and get the city itself as a {@code CITY}.
 * <p>
 * Wikidata's {@code P131} trees overlap (Kharkiv oblast's tree reaches Poltava and Sumy places), so a place can come
 * back for several regions. Only regions whose bounding box (plus {@value #BOUNDS_MARGIN_DEGREES} degrees) holds the
 * place's coordinate count; a city region wins over the oblast around it, otherwise the region with the nearest
 * centre. A place no candidate region's box holds is dropped as misplaced. Areas too large for the kind of place
 * ({@link #MAX_AREA_KM2}) are forgotten.
 * <p>
 * The User-Agent and the pause between requests come from the system properties {@code nashi.http.user-agent} and
 * {@code nashi.wiki.request-gap} ({@code 1s}, {@code 1500ms}), with the {@link PoliteWikiHttp} defaults.
 */
public final class PlaceSeedGenerator {

    private static final Logger log = LoggerFactory.getLogger(PlaceSeedGenerator.class);

    static final String ENDPOINT = "https://query.wikidata.org/sparql";
    static final String SEED_FILE = "places-ua.json.gz";
    static final String VERSION_FILE = "places-ua.version";

    /**
     * Settlements of one scope ({@code %SCOPE%} binds {@code ?place}). The types are a {@code FILTER}, not
     * {@code VALUES}: with {@code VALUES} WDQS starts from every instance of «village» and «city» in the world and
     * times out. The area is the best-rank value normalized to square metres ({@code psn:}), because about a tenth
     * of the areas are entered in hectares. Populations come with their date ({@code P585}) so the parser can keep
     * the latest of a series. Places Wikidata marks as dissolved or abolished ({@code P576}) or as former or
     * abandoned ({@link PlaceTypes#excludedQids()}) are left out.
     */
    static final String PLACES_QUERY = """
            SELECT ?place ?label ?coord ?type ?areaM2 ?pop ?popDate WHERE {
              %SCOPE%
              ?place wdt:P625 ?coord ; wdt:P31 ?type .
              FILTER(?type IN (%TYPES%))
              ?place rdfs:label ?label FILTER(LANG(?label) = "uk")
              OPTIONAL { ?place p:P2046 ?areaSt . ?areaSt a wikibase:BestRank ;
                                psn:P2046/wikibase:quantityAmount ?areaM2 }
              OPTIONAL { ?place p:P1082 ?popSt . ?popSt a wikibase:BestRank ; ps:P1082 ?pop .
                         OPTIONAL { ?popSt pq:P585 ?popDate } }
              FILTER NOT EXISTS { ?place wdt:P576 [] }
              FILTER NOT EXISTS { ?place wdt:P31 ?gone . VALUES ?gone { %EXCLUDED% } }
            }
            """;

    /** Children of an area that have children of their own (raions, hromadas, city districts). */
    static final String SUBUNITS_QUERY = """
            SELECT DISTINCT ?item WHERE {
              ?item wdt:P131 wd:%AREA% .
              FILTER EXISTS { ?sub wdt:P131 ?item }
            }
            """;

    /**
     * {@code P31} types of everything with a coordinate in one region. Asking for the whole country at once
     * ({@code P17 = Q212}) times out on WDQS, and so does a {@code P279*} test inside this query, so the generator
     * asks region by region, adds the counts up and keeps the settlement types with {@link #SETTLEMENT_TYPES_QUERY}.
     * Kyiv's {@code P131} tree (every street and building) is too big for {@code P131+}; three levels reach its
     * settlements.
     */
    static final String TYPES_QUERY = """
            SELECT ?type (COUNT(DISTINCT ?place) AS ?n) WHERE {
              ?place %PATH% wd:%AREA% ; wdt:P31 ?type ; wdt:P625 [] .
            } GROUP BY ?type
            """;

    /** Which of these types are human settlements ({@code Q486972} or a subclass), with their labels. */
    static final String SETTLEMENT_TYPES_QUERY = """
            SELECT ?type ?uk ?en WHERE {
              VALUES ?type { %TYPES% }
              ?type wdt:P279* wd:Q486972 .
              OPTIONAL { ?type rdfs:label ?uk FILTER(LANG(?uk) = "uk") }
              OPTIONAL { ?type rdfs:label ?en FILTER(LANG(?en) = "en") }
            }
            """;

    /** Kyiv and Sevastopol: settlements lie at most three {@code P131} levels below the city. */
    static final String CITY_PATH = "wdt:P131/wdt:P131?/wdt:P131?";

    /**
     * Where to look for the settlements of the two cities: the city itself, and for Sevastopol also
     * «Севастопольська міськрада» ({@code Q2659239}), under which Wikidata files its villages (and which it places
     * in Crimea).
     */
    static final Map<String, List<String>> CITY_ROOTS = Map.of(
            "Q1899", List.of("Q1899"),
            "Q7525", List.of("Q7525", "Q2659239"));

    static final int CHUNK = 25;
    static final int MAX_SPLIT_DEPTH = 3;
    static final int MAX_FAILURES_IN_A_ROW = 12;
    static final double BOUNDS_MARGIN_DEGREES = 0.1;

    /**
     * Largest believable area per kind of place, in km². Wikidata gives many villages and towns the area of their
     * hromada, or a value in the wrong unit (a village of 600 people at 37 378 km²); such an area would spread pins
     * over 8 km, so it is dropped and the pin radius falls back to the type's default. Kyiv (848) and Sevastopol
     * (864, with its rural territory) are the largest cities.
     */
    static final Map<PlaceType, Double> MAX_AREA_KM2 = Map.of(
            PlaceType.CITY, 1000.0, PlaceType.TOWN, 50.0, PlaceType.VILLAGE, 30.0);

    /** Q-numbers in numeric order: Q1899 before Q7525 before Q36036. */
    public static final Comparator<String> QID_ORDER =
            Comparator.comparingLong((String qid) -> Long.parseLong(qid.substring(1)));

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WikiHttp http;
    private final Map<Region, RegionBox> regions;
    private final List<String> skipped = new ArrayList<>();
    private int failuresInARow;
    private int outOfBounds;
    private int ambiguous;
    private int implausibleAreas;

    PlaceSeedGenerator(WikiHttp http) {
        this.http = http;
        this.regions = loadRegions();
    }

    public static void main(String[] args) throws IOException {
        PoliteWikiHttp http = new PoliteWikiHttp(
                System.getProperty("nashi.http.user-agent", PoliteWikiHttp.DEFAULT_USER_AGENT),
                requestGap(System.getProperty("nashi.wiki.request-gap")));
        PlaceSeedGenerator generator = new PlaceSeedGenerator(http);
        if (args.length == 1 && args[0].equals("--types")) {
            generator.printTypes();
        } else if (args.length == 2 && args[0].equals("--out")) {
            List<PlaceSeedRow> rows = generator.collect();
            if (!generator.skipped.isEmpty()) {
                throw new IllegalStateException("Areas WDQS could not answer: " + generator.skipped
                        + "; nothing written, run again later");
            }
            Path dir = Path.of(args[1]);
            String version = write(rows, dir, LocalDate.now(ZoneId.of("Europe/Kyiv")));
            System.out.println(summary(rows, generator.outOfBounds, generator.ambiguous, generator.implausibleAreas));
            System.out.printf("Wrote %d places to %s (version %s, %d bytes)%n", rows.size(), dir.resolve(SEED_FILE),
                    version, Files.size(dir.resolve(SEED_FILE)));
        } else {
            throw new IllegalArgumentException("Usage: PlaceSeedGenerator --out <dir> | --types");
        }
    }

    static Duration requestGap(String value) {
        return value == null || value.isBlank()
                ? PoliteWikiHttp.DEFAULT_REQUEST_GAP
                : DurationStyle.detectAndParse(value);
    }

    /** Every region's settlements, one row per QID (see the class comment), sorted by QID number. */
    List<PlaceSeedRow> collect() {
        List<PlaceSeedRow> candidates = new ArrayList<>();
        for (String city : UkraineRegions.listCityQids()) {
            Region region = UkraineRegions.fromQid(city).orElseThrow();
            List<PlaceSeedRow> rows = new ArrayList<>();
            for (PlaceSeedRow itself : fetch("VALUES ?place { wd:" + city + " }", region)) {
                rows.add(new PlaceSeedRow(itself.qid(), itself.name(), PlaceType.CITY, region, itself.lat(),
                        itself.lon(), itself.areaKm2(), itself.population()));
            }
            rows.addAll(fetch(cityScope(city), region));
            log.info("[PLACES] {} {}: {} places", region, city, rows.size());
            candidates.addAll(rows);
        }
        for (String oblast : UkraineRegions.listOblastQids()) {
            Region region = UkraineRegions.fromQid(oblast).orElseThrow();
            List<PlaceSeedRow> rows = area(oblast, region);
            log.info("[PLACES] {} {}: {} places", region, oblast, rows.size());
            candidates.addAll(rows);
        }
        return resolve(candidates);
    }

    private List<PlaceSeedRow> resolve(List<PlaceSeedRow> candidates) {
        Map<String, List<PlaceSeedRow>> byQid = new LinkedHashMap<>();
        candidates.forEach(row -> byQid.computeIfAbsent(row.qid(), q -> new ArrayList<>()).add(row));
        List<PlaceSeedRow> resolved = new ArrayList<>(byQid.size());
        for (List<PlaceSeedRow> rows : byQid.values()) {
            List<PlaceSeedRow> inside = rows.stream().filter(this::insideRegion).toList();
            if (inside.isEmpty()) {
                outOfBounds++;
                PlaceSeedRow row = rows.getFirst();
                log.info("[PLACES] {} {} ({}, {}) lies outside {}, dropped", row.qid(), row.name(), row.lat(),
                        row.lon(), rows.stream().map(PlaceSeedRow::region).distinct().toList());
                continue;
            }
            if (inside.stream().map(PlaceSeedRow::region).distinct().count() > 1) {
                ambiguous++;
            }
            PlaceSeedRow chosen = inside.stream()
                    .min(Comparator.comparingInt((PlaceSeedRow r) -> isCity(r.region()) ? 0 : 1)
                            .thenComparingDouble(this::distanceToCentre))
                    .orElseThrow();
            resolved.add(plausibleArea(chosen));
        }
        return sortedByQid(resolved);
    }

    /**
     * One {@code UNION} branch per root: with the root as a variable ({@code VALUES ?root}) WDQS walks every
     * {@code P131} statement and times out.
     */
    static String cityScope(String city) {
        return CITY_ROOTS.get(city).stream()
                .map(root -> "{ ?place " + CITY_PATH + " wd:" + root + " . }")
                .collect(Collectors.joining(" UNION "));
    }

    static List<PlaceSeedRow> sortedByQid(Collection<PlaceSeedRow> rows) {
        return rows.stream().sorted(Comparator.comparing(PlaceSeedRow::qid, QID_ORDER)).toList();
    }

    private static boolean isCity(Region region) {
        return region == Region.KYIV_CITY || region == Region.SEVASTOPOL_CITY;
    }

    /** Degrees on a plane scaled by the cosine of the latitude: enough to rank two nearby centres. */
    private double distanceToCentre(PlaceSeedRow row) {
        double[] centre = regions.get(row.region()).center();
        double dx = (row.lon() - centre[0]) * Math.cos(Math.toRadians(row.lat()));
        double dy = row.lat() - centre[1];
        return Math.hypot(dx, dy);
    }

    /** Areas left out because even the smallest split failed; {@link #main} refuses to write then. */
    List<String> skipped() {
        return List.copyOf(skipped);
    }

    private List<PlaceSeedRow> area(String area, Region region) {
        try {
            return fetch("?place wdt:P131+ wd:" + area + " .", region);
        } catch (RuntimeException e) {
            if (!tooHeavy(e)) {
                throw e;
            }
            log.info("[PLACES] {} is too big for one query ({}), splitting it", area, firstLine(e));
            return split(area, region, 1);
        }
    }

    private List<PlaceSeedRow> split(String area, Region region, int depth) {
        List<PlaceSeedRow> rows = new ArrayList<>(fetch("?place wdt:P131 wd:" + area + " .", region));
        List<String> subunits = PlaceSparqlParser.parseQids(get(SUBUNITS_QUERY.replace("%AREA%", area)), "item");
        for (int from = 0; from < subunits.size(); from += CHUNK) {
            parents(subunits.subList(from, Math.min(subunits.size(), from + CHUNK)), region, depth, rows);
        }
        return rows;
    }

    private void parents(List<String> parents, Region region, int depth, List<PlaceSeedRow> rows) {
        try {
            rows.addAll(fetch("VALUES ?parent { " + values(parents) + " } ?place wdt:P131+ ?parent .", region));
        } catch (RuntimeException e) {
            if (!tooHeavy(e)) {
                throw e;
            }
            if (parents.size() > 1) {
                int middle = parents.size() / 2;
                parents(parents.subList(0, middle), region, depth, rows);
                parents(parents.subList(middle, parents.size()), region, depth, rows);
            } else if (depth < MAX_SPLIT_DEPTH) {
                log.info("[PLACES] {} is too big for one query, splitting it", parents.getFirst());
                rows.addAll(split(parents.getFirst(), region, depth + 1));
            } else {
                log.warn("[PLACES] {} skipped: {}", parents.getFirst(), firstLine(e));
                skipped.add(parents.getFirst());
            }
        }
    }

    private List<PlaceSeedRow> fetch(String scope, Region region) {
        return PlaceSparqlParser.parse(get(placesQuery(scope)), region);
    }

    static String placesQuery(String scope) {
        return PLACES_QUERY.replace("%SCOPE%", scope)
                .replace("%TYPES%", values(PlaceTypes.qids(), ", "))
                .replace("%EXCLUDED%", values(PlaceTypes.excludedQids().stream().sorted().toList()));
    }

    private static String values(Collection<String> qids) {
        return values(qids, " ");
    }

    private static String values(Collection<String> qids, String separator) {
        return qids.stream().map(q -> "wd:" + q).collect(Collectors.joining(separator));
    }

    /** One request; a run of {@value #MAX_FAILURES_IN_A_ROW} failures means WDQS is down, not that queries are big. */
    private String get(String query) {
        try {
            String body = http.get(url(query));
            JSON.readTree(body);
            failuresInARow = 0;
            return body;
        } catch (RuntimeException e) {
            if (++failuresInARow >= MAX_FAILURES_IN_A_ROW) {
                throw new WikiFetchException("WDQS failed " + failuresInARow + " times in a row", e);
            }
            throw e;
        }
    }

    /** Whether a smaller query may succeed: the endpoint ran out of time or cut the answer off. */
    static boolean tooHeavy(RuntimeException e) {
        if (e instanceof JacksonException || e instanceof PlaceSparqlParser.TruncatedAnswerException) {
            return true;
        }
        if (e instanceof WikiFetchException fetch) {
            int status = fetch.httpStatus().orElse(0);
            return fetch.timedOut() || status == 500 || status == 502 || status == 503 || status == 504;
        }
        return false;
    }

    private boolean insideRegion(PlaceSeedRow row) {
        double[] b = regions.get(row.region()).bbox();
        return row.lon() >= b[0] - BOUNDS_MARGIN_DEGREES && row.lat() >= b[1] - BOUNDS_MARGIN_DEGREES
                && row.lon() <= b[2] + BOUNDS_MARGIN_DEGREES && row.lat() <= b[3] + BOUNDS_MARGIN_DEGREES;
    }

    private PlaceSeedRow plausibleArea(PlaceSeedRow row) {
        Double max = MAX_AREA_KM2.get(row.type());
        if (row.areaKm2() == null || max == null || row.areaKm2() <= max) {
            return row;
        }
        implausibleAreas++;
        return new PlaceSeedRow(row.qid(), row.name(), row.type(), row.region(), row.lat(), row.lon(), null,
                row.population());
    }

    /** Centre and bounding box ({@code [lon, lat]}, {@code [w, s, e, n]}) of a region. */
    record RegionBox(Region code, double[] center, double[] bbox) {
    }

    private static Map<Region, RegionBox> loadRegions() {
        try (InputStream in = new ClassPathResource("geo/regions-meta.json").getInputStream()) {
            Map<Region, RegionBox> regions = new EnumMap<>(Region.class);
            for (RegionBox r : JSON.readValue(in, RegionBox[].class)) {
                regions.put(r.code(), r);
            }
            if (regions.size() != Region.values().length) {
                throw new IllegalStateException("geo/regions-meta.json has " + regions.size() + " regions");
            }
            return regions;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read geo/regions-meta.json", e);
        }
    }

    /**
     * Writes the seed and its version into {@code dir}.
     *
     * @return the version, {@code <date>+<first 8 hex digits of SHA-256 of the uncompressed lines>}
     */
    static String write(List<PlaceSeedRow> rows, Path dir, LocalDate date) throws IOException {
        byte[] lines = jsonLines(rows);
        Files.createDirectories(dir);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(dir.resolve(SEED_FILE)))) {
            out.write(lines);
        }
        String version = date + "+" + contentHash(lines);
        Files.writeString(dir.resolve(VERSION_FILE), version + "\n", StandardCharsets.UTF_8);
        return version;
    }

    static byte[] jsonLines(List<PlaceSeedRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (PlaceSeedRow row : rows) {
            out.writeBytes(JSON.writeValueAsBytes(row));
            out.write('\n');
        }
        return out.toByteArray();
    }

    public static String contentHash(byte[] jsonLines) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(jsonLines);
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String summary(List<PlaceSeedRow> rows, int outOfBounds, int ambiguous, int implausibleAreas) {
        Map<Region, Long> perRegion = rows.stream().collect(Collectors.groupingBy(PlaceSeedRow::region,
                () -> new EnumMap<>(Region.class), Collectors.counting()));
        Map<PlaceType, Long> perType = rows.stream().collect(Collectors.groupingBy(PlaceSeedRow::type,
                () -> new EnumMap<>(PlaceType.class), Collectors.counting()));
        long withArea = rows.stream().filter(r -> r.areaKm2() != null).count();
        long withPopulation = rows.stream().filter(r -> r.population() != null).count();
        StringBuilder s = new StringBuilder();
        s.append("places: ").append(rows.size()).append(", with area: ").append(withArea)
                .append(", with population: ").append(withPopulation).append(", dropped outside their region: ")
                .append(outOfBounds).append(", in two regions' boxes: ").append(ambiguous)
                .append(", implausible areas forgotten: ").append(implausibleAreas).append('\n');
        perType.forEach((type, n) -> s.append("  ").append(type).append(": ").append(n).append('\n'));
        perRegion.forEach((region, n) -> s.append("  ").append(region).append(": ").append(n).append('\n'));
        return s.toString();
    }

    void printTypes() {
        Map<String, Long> counts = new HashMap<>();
        List<String> areas = new ArrayList<>(UkraineRegions.listOblastQids());
        areas.addAll(UkraineRegions.listCityQids());
        for (String area : areas) {
            String path = UkraineRegions.listCityQids().contains(area) ? CITY_PATH : "wdt:P131+";
            JsonNode rows = bindings(TYPES_QUERY.replace("%PATH%", path).replace("%AREA%", area));
            System.err.printf("%s: %d types%n", area, rows.size());
            for (JsonNode b : rows) {
                counts.merge(qid(b.path("type").path("value").asString()),
                        Long.parseLong(b.path("n").path("value").asString()), Long::sum);
            }
        }
        Map<String, String> settlementTypes = new HashMap<>();
        List<String> all = List.copyOf(counts.keySet());
        for (int from = 0; from < all.size(); from += 200) {
            String values = all.subList(from, Math.min(all.size(), from + 200)).stream()
                    .map(q -> "wd:" + q).collect(Collectors.joining(" "));
            for (JsonNode b : bindings(SETTLEMENT_TYPES_QUERY.replace("%TYPES%", values))) {
                settlementTypes.put(qid(b.path("type").path("value").asString()),
                        b.path("uk").path("value").asString() + " | " + b.path("en").path("value").asString());
            }
        }
        counts.entrySet().stream()
                .filter(e -> settlementTypes.containsKey(e.getKey()))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> System.out.printf("%-12s %7d  %s%n", e.getKey(), e.getValue(),
                        settlementTypes.get(e.getKey())));
    }

    private JsonNode bindings(String query) {
        return JSON.readTree(get(query)).path("results").path("bindings");
    }

    static String url(String query) {
        return ENDPOINT + "?format=json&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    static String qid(String entityUri) {
        return entityUri.substring(entityUri.lastIndexOf('/') + 1);
    }

    private static String firstLine(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
