package com.nashi.geo;

import com.nashi.artist.PinPrecision;
import com.nashi.place.PlaceType;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Places an artist's pin deterministically inside a settlement's radius ({@code docs/02-contracts.md} section 4).
 * The JS reference in {@code contracts/scripts/lib/pin.mjs} must give the same results; the shared vectors in
 * {@code contracts/test-vectors/pins.json} check that.
 */
public final class PinPlacer {

    private PinPlacer() {
    }

    public static GeoJsonPoint pinFor(String artistId, PlaceRef p, PinPrecision precision) {
        if (precision == PinPrecision.EXACT) {
            return new GeoJsonPoint(p.lon(), p.lat());
        }
        double radius = radiusMeters(p.type(), p.areaKm2());
        byte[] h = sha256((artistId + ":" + p.placeId()).getBytes(UTF_8));
        double u1 = Integer.toUnsignedLong(ByteBuffer.wrap(h, 0, 4).getInt()) / 4294967296.0;
        double u2 = Integer.toUnsignedLong(ByteBuffer.wrap(h, 4, 4).getInt()) / 4294967296.0;
        double r = radius * Math.sqrt(u1);
        double theta = 2 * Math.PI * u2;
        double lat = p.lat() + (r * Math.cos(theta)) / 111320.0;
        double lon = p.lon() + (r * Math.sin(theta)) / (111320.0 * Math.cos(Math.toRadians(p.lat())));
        return new GeoJsonPoint(lon, lat);
    }

    /** Radius in metres: from the area when known, otherwise from the settlement type. */
    public static double radiusMeters(PlaceType type, Double areaKm2) {
        if (areaKm2 != null) {
            return Math.clamp(0.6 * Math.sqrt(areaKm2 / Math.PI) * 1000, 150, 8000);
        }
        return switch (type) {
            case CITY -> 3000;
            case TOWN -> 1200;
            case VILLAGE, OTHER -> 400;
        };
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
