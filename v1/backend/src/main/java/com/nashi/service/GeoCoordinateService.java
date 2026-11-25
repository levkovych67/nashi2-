package com.nashi.service;

import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Service for handling geographic coordinate operations including collision detection and offset calculations.
 */
public interface GeoCoordinateService {
    
    /**
     * Adjusts coordinates if there's a collision with existing artist locations.
     * Uses a spiral pattern to offset coordinates by a small distance (meters).
     *
     * @param latitude  Original latitude
     * @param longitude Original longitude
     * @return GeoJsonPoint with potentially adjusted coordinates
     */
    GeoJsonPoint adjustCoordinatesIfCollision(double latitude, double longitude);
    
    /**
     * Checks if coordinates are already occupied by an existing artist.
     *
     * @param latitude  Latitude to check
     * @param longitude Longitude to check
     * @return true if coordinates are occupied
     */
    boolean hasCoordinateCollision(double latitude, double longitude);
}
