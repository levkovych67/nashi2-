package com.nashi.place.seed;

import com.nashi.geo.Region;
import com.nashi.place.PlaceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The committed seed ({@code src/main/resources/geo/places-ua.*}) as the generator wrote it. */
class PlaceSeedFileTest {

    private static byte[] jsonLines;
    private static List<PlaceSeedRow> rows;
    private static Map<String, PlaceSeedRow> byQid;
    private static String version;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = new GZIPInputStream(new ClassPathResource("geo/places-ua.json.gz").getInputStream())) {
            jsonLines = in.readAllBytes();
        }
        try (InputStream in = new ClassPathResource("geo/places-ua.version").getInputStream()) {
            version = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        JsonMapper json = JsonMapper.builder().build();
        rows = new ArrayList<>();
        for (String line : new String(jsonLines, StandardCharsets.UTF_8).split("\n")) {
            rows.add(json.readValue(line, PlaceSeedRow.class));
        }
        byQid = rows.stream().collect(Collectors.toMap(PlaceSeedRow::qid, Function.identity()));
    }

    @Test
    void hasAtLeastTwentyFiveThousandPlaces() {
        assertThat(rows).hasSizeGreaterThanOrEqualTo(25_000);
    }

    @Test
    void everyRegionHasPlaces() {
        Map<Region, Long> perRegion = new EnumMap<>(Region.class);
        rows.forEach(r -> perRegion.merge(r.region(), 1L, Long::sum));

        assertThat(perRegion).containsOnlyKeys(Region.values());
    }

    @Test
    void containsKnownPlaces() {
        assertThat(byQid.get("Q36036")).extracting(PlaceSeedRow::name, PlaceSeedRow::type, PlaceSeedRow::region)
                .containsExactly("Львів", PlaceType.CITY, Region.LVIV);
        assertThat(byQid.get("Q465104")).extracting(PlaceSeedRow::name, PlaceSeedRow::region)
                .containsExactly("Броди", Region.LVIV);
        assertThat(byQid.get("Q1899")).extracting(PlaceSeedRow::name, PlaceSeedRow::type, PlaceSeedRow::region)
                .containsExactly("Київ", PlaceType.CITY, Region.KYIV_CITY);
        assertThat(byQid.get("Q7525")).extracting(PlaceSeedRow::name, PlaceSeedRow::type, PlaceSeedRow::region)
                .containsExactly("Севастополь", PlaceType.CITY, Region.SEVASTOPOL_CITY);
    }

    @Test
    void rowsAreUniqueAndSortedByQidNumber() {
        assertThat(byQid).hasSameSizeAs(rows);
        assertThat(rows).isSortedAccordingTo(Comparator.comparing(PlaceSeedRow::qid, PlaceSeedGenerator.QID_ORDER));
    }

    @Test
    void everyRowIsCompleteAndInsideUkraine() {
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.qid()).matches("Q[1-9][0-9]*");
            assertThat(r.name()).isNotBlank().isEqualTo(r.name().strip());
            assertThat(r.type()).isNotNull().isNotEqualTo(PlaceType.OTHER);
            assertThat(r.region()).isNotNull();
            assertThat(r.lat()).isBetween(44.0, 52.5);
            assertThat(r.lon()).isBetween(22.0, 40.5);
            if (r.areaKm2() != null) {
                assertThat(r.areaKm2()).isPositive();
            }
            if (r.population() != null) {
                assertThat(r.population()).isPositive();
            }
        });
    }

    @Test
    void versionIsTheGenerationDatePlusTheContentHash() {
        assertThat(version).matches("\\d{4}-\\d{2}-\\d{2}\\+[0-9a-f]{8}");
        assertThat(version.substring(version.indexOf('+') + 1)).isEqualTo(PlaceSeedGenerator.contentHash(jsonLines));
    }
}
