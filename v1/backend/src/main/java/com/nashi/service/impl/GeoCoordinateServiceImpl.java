package com.nashi.service.impl;

import com.nashi.domain.Artist;
import com.nashi.repo.ArtistRepository;
import com.nashi.service.GeoCoordinateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoCoordinateServiceImpl implements GeoCoordinateService {

    private final ArtistRepository artistRepository;

    // Minimum distance between map pins for visual clarity
    private static final double MIN_VISUAL_GAP_METERS = 25.0;
    
    // Starting radius for spiral search
    private static final double INITIAL_RADIUS_METERS = 30.0;
    
    // How much to increase radius each ring
    private static final double RADIUS_INCREMENT_METERS = 15.0;
    
    // Maximum search radius before giving up
    private static final double MAX_SEARCH_RADIUS_METERS = 200.0;
    
    // Number of positions to try per ring
    private static final int POSITIONS_PER_RING = 8;

    @Override
    public GeoJsonPoint adjustCoordinatesIfCollision(double targetLat, double targetLon) {
        return new GeoJsonPoint(targetLon, targetLat);
    }

    @Override
    public boolean hasCoordinateCollision(double latitude, double longitude) {
        Point point = new Point(longitude, latitude);
        List<Artist> nearbyArtists = artistRepository.findByLocationNearAndStatus(
            point,
            new Distance(MIN_VISUAL_GAP_METERS / 1000.0, Metrics.KILOMETERS),
            null
        );
        return !nearbyArtists.isEmpty();
    }

    /**
     * Searches for a safe position using expanding spiral pattern.
     * Systematically checks positions in concentric rings until a collision-free spot is found.
     */
    private GeoJsonPoint findSafePositionSpiral(double centerLat, double centerLon, List<Artist> nearbyArtists) {
        int totalAttempts = 0;
        double currentRadius = INITIAL_RADIUS_METERS;

        while (currentRadius <= MAX_SEARCH_RADIUS_METERS) {
            // Try multiple positions around this radius
            double angleIncrement = 360.0 / POSITIONS_PER_RING;
            
            for (int i = 0; i < POSITIONS_PER_RING; i++) {
                double angle = i * angleIncrement;
                totalAttempts++;
                
                // Calculate candidate position
                double[] candidate = calculateOffset(centerLat, centerLon, currentRadius, angle);
                double candLat = candidate[0];
                double candLon = candidate[1];
                
                // Check if this position is safe (minimum 25m from all artists)
                if (isPositionSafe(candLat, candLon, nearbyArtists)) {
                    log.info("Found collision-free position at [{}, {}] after {} attempts. " +
                            "Distance from center: {}m, Angle: {}°",
                            candLat, candLon, totalAttempts, Math.round(currentRadius), Math.round(angle));
                    return new GeoJsonPoint(candLon, candLat);
                }
            }
            
            // No safe position found in this ring, expand radius
            currentRadius += RADIUS_INCREMENT_METERS;
            log.debug("No safe position found at {}m radius. Expanding to {}m...", 
                     currentRadius - RADIUS_INCREMENT_METERS, currentRadius);
        }

        // Should rarely happen - means area is extremely dense
        log.error("Failed to find collision-free position after {} attempts within {}m radius", 
                 totalAttempts, MAX_SEARCH_RADIUS_METERS);
        throw new IllegalStateException(
            String.format("Unable to find collision-free coordinates after %d attempts. " +
                         "Area around [%f, %f] is too crowded (tried up to %dm radius)",
                         totalAttempts, centerLat, centerLon, (int)MAX_SEARCH_RADIUS_METERS));
    }

    /**
     * Checks if a position is safe (at least MIN_VISUAL_GAP_METERS away from ALL existing artists).
     */
    private boolean isPositionSafe(double lat, double lon, List<Artist> existingArtists) {
        for (Artist artist : existingArtists) {
            double artistLat = artist.getLocation().getY();
            double artistLon = artist.getLocation().getX();
            
            double distance = calculateDistanceMeters(lat, lon, artistLat, artistLon);
            
            if (distance < MIN_VISUAL_GAP_METERS) {
                log.debug("Position [{}, {}] rejected: too close to existing artist ({} meters)", 
                         lat, lon, Math.round(distance));
                return false;
            }
        }
        return true;
    }

    /**
     * Calculates a new position given center coordinates, distance (meters), and bearing (degrees).
     * Uses accurate spherical Earth calculations.
     */
    private double[] calculateOffset(double lat, double lon, double distMeters, double bearingDeg) {
        double earthRadius = 6371000.0; // Earth radius in meters
        double distRad = distMeters / earthRadius;
        double bearingRad = Math.toRadians(bearingDeg);
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);

        double newLatRad = Math.asin(
            Math.sin(latRad) * Math.cos(distRad) +
            Math.cos(latRad) * Math.sin(distRad) * Math.cos(bearingRad)
        );
        
        double newLonRad = lonRad + Math.atan2(
            Math.sin(bearingRad) * Math.sin(distRad) * Math.cos(latRad),
            Math.cos(distRad) - Math.sin(latRad) * Math.sin(newLatRad)
        );

        return new double[]{Math.toDegrees(newLatRad), Math.toDegrees(newLonRad)};
    }

    /**
     * Calculates distance between two coordinates using Haversine formula.
     * Returns distance in meters.
     */
    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000.0; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return earthRadius * c;
    }
}
