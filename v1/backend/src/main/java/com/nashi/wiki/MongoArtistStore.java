package com.nashi.wiki;

import com.nashi.domain.Artist;
import com.nashi.enums.ArtistSource;
import com.nashi.enums.ArtistStatus;
import com.nashi.repo.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MongoArtistStore implements ArtistStore {

    private final MongoTemplate mongoTemplate;
    private final ArtistRepository artistRepository;

    @Override
    public KnownArtists loadKnown() {
        Query query = new Query();
        query.fields().include("wikidataId").include("wikiTitle").include("name").include("status");

        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        Set<String> manualNames = new HashSet<>();
        for (Artist artist : mongoTemplate.find(query, Artist.class)) {
            if (artist.getWikidataId() != null) {
                ids.add(artist.getWikidataId());
            }
            if (artist.getWikiTitle() != null) {
                titles.add(artist.getWikiTitle());
            }
            if (artist.getWikidataId() == null && artist.getStatus() != ArtistStatus.REJECTED) {
                manualNames.add(NameNormalizer.normalize(artist.getName()));
            }
        }
        return new KnownArtists(ids, titles, manualNames);
    }

    @Override
    public boolean slugExists(String slug) {
        return artistRepository.existsBySlug(slug);
    }

    @Override
    public long countWikipediaArtists() {
        return mongoTemplate.count(Query.query(Criteria.where("source").is(ArtistSource.WIKIPEDIA)), Artist.class);
    }

    @Override
    public Artist save(Artist artist) {
        return artistRepository.save(artist);
    }
}
