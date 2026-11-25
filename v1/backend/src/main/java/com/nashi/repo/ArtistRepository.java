package com.nashi.repo;


import com.nashi.domain.Artist;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistRepository extends MongoRepository<Artist, String> {

    /**
     * Finds an artist by their unique slug (e.g., "dakhabrakha").
     * Used for public profile pages.
     */
    Optional<Artist> findBySlug(String slug);

    Optional<Artist> findByWikidataId(String wikidataId);

    /**
     * Finds all APPROVED artists with pagination.
     * This is the main query for the public "Browse Artists" page.
     */
    Page<Artist> findByStatus(ArtistStatus status, Pageable pageable);

    /**
     * Finds artists by Region and Status.
     * Useful for "Show me bands from Lviv".
     */
    Page<Artist> findByRegionAndStatus(Region region, ArtistStatus status, Pageable pageable);

    /**
     * Finds artists by Style/Genre and Status.
     */
    Page<Artist> findByStyleAndStatus(String style, ArtistStatus status, Pageable pageable);

    /**
     * GEOSPATIAL QUERY:
     * Finds approved artists near a specific location within a given distance.
     * * @param location The center point (longitude/latitude).
     * @param distance The radius (e.g., new Distance(50, Metrics.KILOMETERS)).
     * @param status   Usually ArtistStatus.APPROVED.
     */
    List<Artist> findByLocationNearAndStatus(Point location, Distance distance, ArtistStatus status);

    /**
     * Checks if a slug already exists to ensure uniqueness.
     */
    boolean existsBySlug(String slug);

    Page<Artist> findByRegion(Region region, Pageable pageable);

    long countByStatus(ArtistStatus status);
    
    /**
     * Search artists by name (case-insensitive partial match)
     * @param name Search query for artist name
     * @param status Filter by artist status
     * @param pageable Pagination
     */
    Page<Artist> findByNameContainingIgnoreCaseAndStatus(String name, ArtistStatus status, Pageable pageable);
    
    /**
     * Search artists by city (case-insensitive partial match)
     */
    Page<Artist> findByCityContainingIgnoreCaseAndStatus(String city, ArtistStatus status, Pageable pageable);
}
