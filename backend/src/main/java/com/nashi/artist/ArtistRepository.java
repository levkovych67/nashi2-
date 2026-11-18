package com.nashi.artist;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends MongoRepository<Artist, String> {

    Optional<Artist> findBySlug(String slug);

    Optional<Artist> findBySlugAndStatus(String slug, ArtistStatus status);

    Optional<Artist> findByWikidataId(String wikidataId);

    Optional<Artist> findByLegacyV1Id(String legacyV1Id);

    boolean existsBySlug(String slug);

    List<Artist> findByStatusAndNameNormalized(ArtistStatus status, String nameNormalized);
}
