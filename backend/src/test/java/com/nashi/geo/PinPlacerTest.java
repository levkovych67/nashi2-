package com.nashi.geo;

import com.nashi.artist.PinPrecision;
import com.nashi.artist.TestVectors;
import com.nashi.place.PlaceType;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PinPlacerTest {

    private static final PlaceRef KYIV = new PlaceRef("Q1899", 50.45, 30.5236, PlaceType.CITY, 839.0);
    private static final double EARTH_RADIUS_M = 6371008.8;
    private static final long SEED = 20261118L;

    record VectorPlace(String placeId, double lat, double lon, PlaceType type, Double areaKm2) {
    }

    record VectorPoint(double lat, double lon) {
    }

    record PinVector(String name, String artistId, VectorPlace place, PinPrecision precision, VectorPoint expected) {
    }

    static Stream<Arguments> pinsJson() {
        List<PinVector> vectors = TestVectors.read("pins.json", new TypeToken<List<PinVector>>() { }.getType());
        return vectors.stream().map(v -> Arguments.of(v.name(), v));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pinsJson")
    void matchesJsReferenceVectors(String name, PinVector v) {
        VectorPlace p = v.place();
        PlaceRef place = new PlaceRef(p.placeId(), p.lat(), p.lon(), p.type(), p.areaKm2());

        GeoJsonPoint pin = PinPlacer.pinFor(v.artistId(), place, v.precision());

        assertThat(pin.getY()).isCloseTo(v.expected().lat(), within(1e-9));
        assertThat(pin.getX()).isCloseTo(v.expected().lon(), within(1e-9));
    }

    @Test
    void exactReturnsPlaceCoordinates() {
        GeoJsonPoint pin = PinPlacer.pinFor("65a1b2c3d4e5f60718293a41", KYIV, PinPrecision.EXACT);

        assertThat(pin.getX()).isEqualTo(30.5236);
        assertThat(pin.getY()).isEqualTo(50.45);
    }

    @Test
    void deterministicAcrossCalls() {
        String artistId = "65a1b2c3d4e5f60718293a41";

        GeoJsonPoint first = PinPlacer.pinFor(artistId, KYIV, PinPrecision.SETTLEMENT);
        GeoJsonPoint second = PinPlacer.pinFor(artistId, KYIV, PinPrecision.SETTLEMENT);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void staysWithinRadius() {
        for (String artistId : randomArtistIds(10_000)) {
            GeoJsonPoint pin = PinPlacer.pinFor(artistId, KYIV, PinPrecision.SETTLEMENT);

            assertThat(haversineMeters(KYIV.lat(), KYIV.lon(), pin.getY(), pin.getX())).isLessThanOrEqualTo(8000 + 1);
        }
    }

    @Test
    void radiusRules() {
        assertThat(PinPlacer.radiusMeters(PlaceType.CITY, null)).isEqualTo(3000);
        assertThat(PinPlacer.radiusMeters(PlaceType.TOWN, null)).isEqualTo(1200);
        assertThat(PinPlacer.radiusMeters(PlaceType.VILLAGE, null)).isEqualTo(400);
        assertThat(PinPlacer.radiusMeters(PlaceType.CITY, 839.0)).isEqualTo(8000);
        assertThat(PinPlacer.radiusMeters(PlaceType.VILLAGE, 0.01)).isEqualTo(150);
        assertThat(PinPlacer.radiusMeters(PlaceType.CITY, 50.0)).isCloseTo(2393.65, within(0.01));
    }

    @Test
    void spreadIsUniformByArea() {
        // Uniform over the disk: half the radius covers a quarter of the area.
        int inner = 0;
        List<String> ids = randomArtistIds(10_000);
        for (String artistId : ids) {
            GeoJsonPoint pin = PinPlacer.pinFor(artistId, KYIV, PinPrecision.SETTLEMENT);
            if (haversineMeters(KYIV.lat(), KYIV.lon(), pin.getY(), pin.getX()) <= 8000 / 2.0) {
                inner++;
            }
        }
        assertThat((double) inner / ids.size()).isCloseTo(0.25, within(0.02));
    }

    @Test
    void differentArtistsSamePlaceDiffer() {
        GeoJsonPoint a = PinPlacer.pinFor("65a1b2c3d4e5f60718293a41", KYIV, PinPrecision.SETTLEMENT);
        GeoJsonPoint b = PinPlacer.pinFor("65a1b2c3d4e5f60718293a42", KYIV, PinPrecision.SETTLEMENT);

        assertThat(b).isNotEqualTo(a);
    }

    /** 24-hex ObjectId-like strings from a fixed seed, so the statistical tests are deterministic. */
    private static List<String> randomArtistIds(int count) {
        Random random = new Random(SEED);
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> {
            byte[] bytes = new byte[12];
            random.nextBytes(bytes);
            return HexFormat.of().formatHex(bytes);
        }).toList();
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
