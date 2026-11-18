package com.nashi.place.seed;

import com.nashi.geo.Region;
import com.nashi.place.PlaceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceSparqlParserTest {

    static final String CITY = "Q12131624";    // місто України
    static final String TOWN = "Q4100864";     // селище міського типу України
    static final String VILLAGE = "Q21672098"; // село України

    static String response(String... bindings) {
        return """
                {"head":{"vars":["place","label","coord","type","areaM2","pop"]},
                 "results":{"bindings":[%s]}}""".formatted(String.join(",", bindings));
    }

    static String binding(String qid, String label, String lang, String coord, String typeQid, String areaM2,
                          String pop) {
        return binding(qid, label, lang, coord, typeQid, areaM2, pop, null);
    }

    static String binding(String qid, String label, String lang, String coord, String typeQid, String areaM2,
                          String pop, String popDate) {
        StringBuilder b = new StringBuilder("{");
        b.append("\"place\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/").append(qid).append("\"}");
        if (label != null) {
            b.append(",\"label\":{\"xml:lang\":\"").append(lang).append("\",\"type\":\"literal\",\"value\":\"")
                    .append(label).append("\"}");
        }
        if (coord != null) {
            b.append(",\"coord\":{\"datatype\":\"http://www.opengis.net/ont/geosparql#wktLiteral\",")
                    .append("\"type\":\"literal\",\"value\":\"").append(coord).append("\"}");
        }
        b.append(",\"type\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/").append(typeQid)
                .append("\"}");
        if (areaM2 != null) {
            b.append(",\"areaM2\":{\"datatype\":\"http://www.w3.org/2001/XMLSchema#decimal\",\"type\":\"literal\",")
                    .append("\"value\":\"").append(areaM2).append("\"}");
        }
        if (pop != null) {
            b.append(",\"pop\":{\"datatype\":\"http://www.w3.org/2001/XMLSchema#decimal\",\"type\":\"literal\",")
                    .append("\"value\":\"").append(pop).append("\"}");
        }
        if (popDate != null) {
            b.append(",\"popDate\":{\"datatype\":\"http://www.w3.org/2001/XMLSchema#dateTime\",")
                    .append("\"type\":\"literal\",\"value\":\"").append(popDate).append("\"}");
        }
        return b.append('}').toString();
    }

    @Test
    void readsQidLabelPointTypeAreaAndPopulation() {
        String json = response(binding("Q42", "Броди", "uk", "Point(25.154166666 50.078055555)",
                CITY, "8670000", "23454"));

        List<PlaceSeedRow> rows = PlaceSparqlParser.parse(json, Region.LVIV);

        assertThat(rows).containsExactly(new PlaceSeedRow("Q42", "Броди", PlaceType.CITY, Region.LVIV,
                50.078055555, 25.154166666, 8.67, 23454L));
    }

    @Test
    void keepsTheLargestAreaAndPopulationOfOnePlaceWhenUndated() {
        String json = response(
                binding("Q7", "Ірпінь", "uk", "Point(30.25 50.52)", CITY, "1000000", "65000"),
                binding("Q7", "Ірпінь", "uk", "Point(30.25 50.52)", CITY, "2000000", "60000"),
                binding("Q7", "Ірпінь", "uk", "Point(30.25 50.52)", CITY, null, null));

        assertThat(PlaceSparqlParser.parse(json, Region.KYIV)).singleElement().satisfies(row -> {
            assertThat(row.population()).isEqualTo(65000L);
            assertThat(row.areaKm2()).isEqualTo(2.0);
        });
    }

    @Test
    void keepsTheLatestPopulationOfASeries() {
        String json = response(
                binding("Q7", "Рудно", "uk", "Point(23.87 49.84)", TOWN, null, "7502", "2001-12-05T00:00:00Z"),
                binding("Q7", "Рудно", "uk", "Point(23.87 49.84)", TOWN, null, "7244", "2022-01-01T00:00:00Z"),
                binding("Q7", "Рудно", "uk", "Point(23.87 49.84)", TOWN, null, "9000", null));

        assertThat(PlaceSparqlParser.parse(json, Region.LVIV).getFirst().population()).isEqualTo(7244L);
    }

    @Test
    void roundsAreaToSquareMetrePrecisionInKm2() {
        String json = response(binding("Q7", "Ірпінь", "uk", "Point(30.25 50.52)", CITY, "8670000.4", null));

        assertThat(PlaceSparqlParser.parse(json, Region.KYIV).getFirst().areaKm2()).isEqualTo(8.67);
    }

    @Test
    void answerWithoutBindingsIsTruncated() {
        assertThatThrownBy(() -> PlaceSparqlParser.parse("{\"head\":{}}", Region.LVIV))
                .isInstanceOf(PlaceSparqlParser.TruncatedAnswerException.class);
    }

    @Test
    void choosesTheSameCoordinateWhateverTheRowOrder() {
        String a = binding("Q8", "Село", "uk", "Point(25.0 49.0)", VILLAGE, null, null);
        String b = binding("Q8", "Село", "uk", "Point(25.1 48.9)", VILLAGE, null, null);

        PlaceSeedRow ab = PlaceSparqlParser.parse(response(a, b), Region.LVIV).getFirst();
        PlaceSeedRow ba = PlaceSparqlParser.parse(response(b, a), Region.LVIV).getFirst();

        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void unknownFiguresStayNull() {
        String json = response(binding("Q9", "Хутір", "uk", "Point(25.0 49.0)", VILLAGE,
                null, null));

        PlaceSeedRow row = PlaceSparqlParser.parse(json, Region.LVIV).getFirst();

        assertThat(row.areaKm2()).isNull();
        assertThat(row.population()).isNull();
    }

    @Test
    void ignoresTypesOutsideTheTable() {
        String json = response(binding("Q10", "Мікрорайон", "uk", "Point(25.0 49.0)", "Q123", null, null));

        assertThat(PlaceSparqlParser.parse(json, Region.LVIV)).isEmpty();
    }

    @Test
    void readsQidLists() {
        String json = """
                {"head":{"vars":["item"]},"results":{"bindings":[
                  {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q100"}},
                  {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q200"}},
                  {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q100"}}]}}""";

        assertThat(PlaceSparqlParser.parseQids(json, "item")).containsExactly("Q100", "Q200");
    }
}
