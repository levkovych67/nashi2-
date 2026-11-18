package com.nashi.place.seed;

import com.nashi.geo.Region;
import com.nashi.place.PlaceType;
import com.nashi.wiki.WikiFetchException;
import com.nashi.wiki.WikiHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.nashi.place.seed.PlaceSparqlParserTest.binding;
import static com.nashi.place.seed.PlaceSparqlParserTest.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class PlaceSeedGeneratorTest {

    private static final String LVIV_OBLAST = "Q164193";
    private static final String VOLYN_OBLAST = "Q167859";
    private static final String MYKOLAIV_OBLAST = "Q181633";
    private static final String CHERKASY_OBLAST = "Q161808";
    private static final String EMPTY = response();
    private static final String CITY = "Q12131624";
    private static final String TOWN = "Q4100864";
    private static final String VILLAGE = "Q21672098";

    private final List<String> queries = new ArrayList<>();

    static String fixture(String name) {
        try (InputStream in = PlaceSeedGeneratorTest.class.getResourceAsStream("/places/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A WDQS stand-in: {@code answer} gets the decoded query and returns the body (or throws). */
    private PlaceSeedGenerator generator(Function<String, String> answer) {
        WikiHttp http = url -> {
            String query = URLDecoder.decode(url.substring(url.indexOf("query=") + 6), StandardCharsets.UTF_8);
            queries.add(query);
            return answer.apply(query);
        };
        return new PlaceSeedGenerator(http);
    }

    private static boolean tree(String query, String area) {
        return query.contains("?place wdt:P131+ wd:" + area + " .");
    }

    private static Map<String, PlaceSeedRow> byQid(List<PlaceSeedRow> rows) {
        return rows.stream().collect(Collectors.toMap(PlaceSeedRow::qid, Function.identity()));
    }

    @Test
    void parsesSparqlRowsIntoSeedRows() {
        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) ? fixture("sparql-lviv.json") : EMPTY).collect();

        Map<String, PlaceSeedRow> byQid = byQid(rows);
        assertThat(rows).hasSize(6);
        assertThat(byQid.get("Q36036")).isEqualTo(new PlaceSeedRow("Q36036", "Львів", PlaceType.CITY, Region.LVIV,
                49.841952, 24.0315921, 182.0, 717273L));
        // Броди is «місто України» in Wikidata, so CITY (the contract's TOWN is «селище, смт»)
        assertThat(byQid.get("Q465104")).isEqualTo(new PlaceSeedRow("Q465104", "Броди", PlaceType.CITY, Region.LVIV,
                50.078055555, 25.154166666, 8.67, 23454L));
        assertThat(byQid.get("Q1973639")).isEqualTo(new PlaceSeedRow("Q1973639", "Рудно", PlaceType.TOWN,
                Region.LVIV, 49.840388888, 23.874666666, 4.19, 7502L));
        assertThat(byQid.get("Q7193281")).isEqualTo(new PlaceSeedRow("Q7193281", "Запитів", PlaceType.TOWN,
                Region.LVIV, 49.920833333, 24.224166666, 8.43, null));
    }

    @Test
    void prefersHigherRankTypeWhenSeveralP31() {
        // Моршин is село, смт and місто in the recorded answer
        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) ? fixture("sparql-lviv.json") : EMPTY).collect();
        assertThat(byQid(rows).get("Q156592").type()).isEqualTo(PlaceType.CITY);

        String townAndVillage = response(
                binding("Q5", "Вузлове", "uk", "Point(24.9 50.2)", VILLAGE, null, null),
                binding("Q5", "Вузлове", "uk", "Point(24.9 50.2)", TOWN, null, null));
        assertThat(PlaceSparqlParser.parse(townAndVillage, Region.LVIV).getFirst().type()).isEqualTo(PlaceType.TOWN);
    }

    @Test
    void dropsRowsWithoutUkLabelOrCoordinates() {
        String json = response(
                binding("Q1", "Без координат", "uk", null, VILLAGE, null, null),
                binding("Q2", null, null, "Point(24.0 49.8)", VILLAGE, null, null),
                binding("Q3", "Russian label", "ru", "Point(24.0 49.8)", VILLAGE, null, null),
                binding("Q4", "Місячне", "uk", "<http://www.wikidata.org/entity/Q405> Point(24.0 49.8)", VILLAGE,
                        null, null),
                binding("Q6", "  ", "uk", "Point(24.0 49.8)", VILLAGE, null, null),
                binding("Q7", "Добре", "uk", "Point(24.0 49.8)", VILLAGE, null, null));

        assertThat(PlaceSparqlParser.parse(json, Region.LVIV)).extracting(PlaceSeedRow::qid).containsExactly("Q7");
    }

    @Test
    void dedupesByQidAcrossOblastQueries() {
        // Inside the boxes of both Volyn and Lviv oblasts, nearer Volyn's centre
        String shared = response(binding("Q42", "Прикордонне", "uk", "Point(24.5 50.6)", VILLAGE, null, "300"));

        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) || tree(q, VOLYN_OBLAST) ? shared : EMPTY)
                .collect();

        assertThat(rows).containsExactly(new PlaceSeedRow("Q42", "Прикордонне", PlaceType.VILLAGE, Region.VOLYN,
                50.6, 24.5, null, 300L));
    }

    @Test
    void aPlaceInTwoTreesGoesToTheRegionWhoseBoxHoldsIt() {
        // Volyn is asked first, but the place lies south of Volyn's box
        String shared = response(binding("Q42", "Львівське", "uk", "Point(23.9 49.8)", VILLAGE, null, null));

        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) || tree(q, VOLYN_OBLAST) ? shared : EMPTY)
                .collect();

        assertThat(rows).extracting(PlaceSeedRow::region).containsExactly(Region.LVIV);
    }

    @Test
    void theNearerRegionCentreWinsWhenBothBoxesHoldThePlace() {
        String shared = response(binding("Q42", "Межове", "uk", "Point(23.9 50.4)", VILLAGE, null, null));

        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) || tree(q, VOLYN_OBLAST) ? shared : EMPTY)
                .collect();

        assertThat(rows).extracting(PlaceSeedRow::region).containsExactly(Region.LVIV);
    }

    @Test
    void aCityRegionWinsOverTheOblastAroundIt() {
        String village = response(binding("Q42", "Орлине", "uk", "Point(33.78 44.44)", VILLAGE, null, null));

        List<PlaceSeedRow> rows = generator(q -> q.contains(PlaceSeedGenerator.CITY_PATH + " wd:Q2659239 .")
                || tree(q, "Q756294") ? village : EMPTY).collect();

        assertThat(rows).extracting(PlaceSeedRow::region).containsExactly(Region.SEVASTOPOL_CITY);
    }

    @Test
    void sevastopolAlsoTakesThePlacesOfItsCityCouncil() {
        // Wikidata puts Sevastopol's villages under «Севастопольська міськрада» (Q2659239), not under the city
        generator(q -> EMPTY).collect();

        assertThat(queries).anySatisfy(q -> assertThat(q).contains(
                "{ ?place " + PlaceSeedGenerator.CITY_PATH + " wd:Q7525 . } UNION { ?place "
                        + PlaceSeedGenerator.CITY_PATH + " wd:Q2659239 . }"));
    }

    @Test
    void addsKyivAndSevastopolThemselvesAsCities() {
        List<PlaceSeedRow> rows = generator(q -> {
            if (q.contains("VALUES ?place { wd:Q1899 }")) {
                return response(binding("Q1899", "Київ", "uk", "Point(30.523611111 50.45)", CITY, "848000000",
                        "2952301"));
            }
            if (q.contains("VALUES ?place { wd:Q7525 }")) {
                // a city typed as something the table ranks lower is still the region's CITY
                return response(binding("Q7525", "Севастополь", "uk", "Point(33.5225 44.605)", TOWN, null, null));
            }
            if (q.contains(PlaceSeedGenerator.CITY_PATH + " wd:Q7525 .")) {
                return response(binding("Q4135", "Орлине", "uk", "Point(33.73 44.44)", VILLAGE, null, "2000"));
            }
            return EMPTY;
        }).collect();

        assertThat(rows).extracting(PlaceSeedRow::qid, PlaceSeedRow::type, PlaceSeedRow::region).containsExactly(
                tuple("Q1899", PlaceType.CITY, Region.KYIV_CITY),
                tuple("Q4135", PlaceType.VILLAGE, Region.SEVASTOPOL_CITY),
                tuple("Q7525", PlaceType.CITY, Region.SEVASTOPOL_CITY));
    }

    @Test
    void splitsAnOblastByItsSubunitsWhenItTimesOut() {
        String raionA = "Q100";
        String raionB = "Q200";
        PlaceSeedGenerator generator = generator(q -> {
            if (tree(q, LVIV_OBLAST)) {
                throw new WikiFetchException("HTTP 504", 504);
            }
            if (q.contains("?place wdt:P131 wd:" + LVIV_OBLAST + " .")) {
                return response(binding("Q1", "Пряме", "uk", "Point(24.0 49.8)", VILLAGE, null, null));
            }
            if (q.contains("?item wdt:P131 wd:" + LVIV_OBLAST)) {
                return """
                        {"head":{"vars":["item"]},"results":{"bindings":[
                          {"item":{"type":"uri","value":"http://www.wikidata.org/entity/%s"}},
                          {"item":{"type":"uri","value":"http://www.wikidata.org/entity/%s"}}]}}"""
                        .formatted(raionA, raionB);
            }
            if (q.contains("VALUES ?parent { wd:" + raionA + " wd:" + raionB + " }")) {
                throw new WikiFetchException("HTTP 502", 502); // both at once is still too much: halve
            }
            if (q.contains("VALUES ?parent { wd:" + raionA + " }")) {
                return response(binding("Q2", "Перше", "uk", "Point(24.1 49.7)", VILLAGE, null, null));
            }
            if (q.contains("VALUES ?parent { wd:" + raionB + " }")) {
                return response(binding("Q3", "Друге", "uk", "Point(24.2 49.6)", TOWN, null, null));
            }
            return EMPTY;
        });

        List<PlaceSeedRow> rows = generator.collect();

        assertThat(rows).extracting(PlaceSeedRow::qid).containsExactly("Q1", "Q2", "Q3");
        assertThat(rows).allSatisfy(r -> assertThat(r.region()).isEqualTo(Region.LVIV));
        assertThat(generator.skipped()).isEmpty();
    }

    @Test
    void skipsAnAreaThatFailsAtTheDeepestSplit() {
        PlaceSeedGenerator generator = generator(q -> {
            if (q.contains("wd:Q164193") || q.contains("wd:Q100")) {
                if (q.contains("SELECT DISTINCT ?item")) {
                    return """
                            {"head":{"vars":["item"]},"results":{"bindings":[
                              {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q100"}}]}}""";
                }
                if (q.contains("?place wdt:P131 wd:")) {
                    return EMPTY;
                }
                throw new WikiFetchException("timeout", new HttpTimeoutException("t"));
            }
            return EMPTY;
        });

        generator.collect();

        assertThat(generator.skipped()).containsExactly("Q100");
    }

    @Test
    void doesNotSplitOnErrorsThatSmallerQueriesWouldNotFix() {
        PlaceSeedGenerator generator = generator(q -> {
            throw new WikiFetchException("HTTP 403", 403);
        });

        assertThatThrownBy(generator::collect)
                .isInstanceOf(WikiFetchException.class);
        assertThat(queries).hasSize(1);
    }

    @Test
    void fallsBackToTheRegionWhoseBoxHoldsThePlace() {
        // Recorded case: Wikidata files Златопіль (a city of Kharkiv oblast) only under Mykolaiv oblast, whose box
        // ends at 33.2° E; its point lies in Kharkiv oblast's box and no other
        String json = response(binding("Q889579", "Златопіль", "uk", "Point(36.214166666 49.386944444)", CITY,
                null, "29573"));

        List<PlaceSeedRow> rows = generator(q -> tree(q, MYKOLAIV_OBLAST) ? json : EMPTY).collect();

        assertThat(rows).containsExactly(new PlaceSeedRow("Q889579", "Златопіль", PlaceType.CITY, Region.KHARKIV,
                49.386944444, 36.214166666, null, 29573L));
    }

    @Test
    void theFallbackPrefersTheNearestCentreAmongBoxesThatHoldThePlace() {
        // 30.0° E 49.8° N lies in the boxes of Vinnytsia, Kyiv and Cherkasy oblasts; Kyiv oblast's centre is nearest
        String json = response(binding("Q2", "Заблукале", "uk", "Point(30.0 49.8)", VILLAGE, null, null));

        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) ? json : EMPTY).collect();

        assertThat(rows).extracting(PlaceSeedRow::qid, PlaceSeedRow::region)
                .containsExactly(tuple("Q2", Region.KYIV));
    }

    @Test
    void dropsAPlaceThatNoRegionBoxHolds() {
        // Recorded case: a village of Volgograd oblast (Russia) filed under Cherkasy oblast
        String json = response(
                binding("Q1", "Справжнє", "uk", "Point(32.0 49.4)", VILLAGE, null, null),
                binding("Q24327821", "Пархоменко (Волгоградська область)", "uk", "Point(44.1422 48.5691)", VILLAGE,
                        null, null));

        List<PlaceSeedRow> rows = generator(q -> tree(q, CHERKASY_OBLAST) ? json : EMPTY).collect();

        assertThat(rows).extracting(PlaceSeedRow::qid).containsExactly("Q1");
    }

    @Test
    void forgetsAreasTooLargeForTheKindOfPlace() {
        // Wikidata gives many villages their hromada's area, or a value in the wrong unit
        String json = response(
                binding("Q1", "Опака", "uk", "Point(23.3 49.4)", VILLAGE, "37378000000", "600"),
                binding("Q2", "Велике село", "uk", "Point(23.4 49.4)", VILLAGE, "29000000", null),
                binding("Q3", "Меденичі", "uk", "Point(23.7 49.4)", TOWN, "600000000", null),
                binding("Q4", "Східниця", "uk", "Point(23.4 49.2)", TOWN, "49000000", null),
                binding("Q5", "Львів", "uk", "Point(24.03 49.84)", CITY, "182000000", null));

        List<PlaceSeedRow> rows = generator(q -> tree(q, LVIV_OBLAST) ? json : EMPTY).collect();

        assertThat(rows).extracting(PlaceSeedRow::qid, PlaceSeedRow::areaKm2).containsExactly(
                tuple("Q1", null), tuple("Q2", 29.0), tuple("Q3", null), tuple("Q4", 49.0), tuple("Q5", 182.0));
    }

    @Test
    void asksForTheTypesOfTheTableAndLeavesOutGonePlaces() {
        generator(q -> EMPTY).collect();

        String oblastQuery = queries.stream().filter(q -> tree(q, LVIV_OBLAST)).findFirst().orElseThrow();
        assertThat(oblastQuery).contains("wd:Q21672098", "wd:Q12131624", "wd:Q4100864",
                "FILTER NOT EXISTS { ?place wdt:P576 [] }", "wd:Q22674925");
        assertThat(queries).hasSize(2 * 2 + 25);
    }

    @Test
    void writesSortedJsonLinesAndAVersionWithTheContentHash(@TempDir Path dir) throws IOException {
        List<PlaceSeedRow> rows = PlaceSeedGenerator.sortedByQid(List.of(
                new PlaceSeedRow("Q36036", "Львів", PlaceType.CITY, Region.LVIV, 49.841952, 24.0315921, 182.0,
                        717273L),
                new PlaceSeedRow("Q1899", "Київ", PlaceType.CITY, Region.KYIV_CITY, 50.45, 30.523611111, null,
                        null)));

        String version = PlaceSeedGenerator.write(rows, dir, LocalDate.of(2026, 11, 18));

        byte[] lines;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(dir.resolve("places-ua.json.gz")))) {
            lines = in.readAllBytes();
        }
        assertThat(new String(lines, StandardCharsets.UTF_8)).isEqualTo("""
                {"qid":"Q1899","name":"Київ","type":"CITY","region":"KYIV_CITY","lat":50.45,"lon":30.523611111}
                {"qid":"Q36036","name":"Львів","type":"CITY","region":"LVIV","lat":49.841952,"lon":24.0315921,\
                "areaKm2":182.0,"population":717273}
                """);
        assertThat(version).isEqualTo("2026-11-18+" + PlaceSeedGenerator.contentHash(lines));
        assertThat(Files.readString(dir.resolve("places-ua.version"))).isEqualTo(version + "\n");
    }
}
