package com.nashi.musician;

import com.nashi.api.NotFoundException;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistRepository;
import com.nashi.artist.ArtistStatus;
import com.nashi.artist.Names;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/** Reads of published musicians for the public API. */
@Service
public class MusicianQueryService {

    private final MongoTemplate mongo;
    private final ArtistRepository artists;
    private final MusicianMapper mapper;

    public MusicianQueryService(MongoTemplate mongo, ArtistRepository artists, MusicianMapper mapper) {
        this.mongo = mongo;
        this.artists = artists;
        this.mapper = mapper;
    }

    public MusicianPage list(MusicianListQuery q) {
        Criteria criteria = Criteria.where("status").is(ArtistStatus.PUBLISHED);
        if (!q.regions().isEmpty()) {
            criteria = criteria.and("origin.region").in(q.regions());
        }
        if (q.q() != null) {
            criteria = criteria.and("nameNormalized").regex(Pattern.quote(Names.normalize(q.q())));
        }
        long total = mongo.count(new Query(criteria), Artist.class);
        Query query = new Query(criteria)
                .with(PageRequest.of(q.page(), q.size(), sortOf(q.sort())))
                .collation(Collation.of("uk"));
        var items = mongo.find(query, Artist.class).stream().map(mapper::toListItem).toList();
        int totalPages = (int) Math.ceil((double) total / q.size());
        return new MusicianPage(items, q.page(), q.size(), total, totalPages);
    }

    public MusicianDetails details(String slug) {
        return artists.findBySlugAndStatus(slug, ArtistStatus.PUBLISHED)
                .map(mapper::toDetails)
                .orElseThrow(() -> new NotFoundException("musician"));
    }

    /** Every sort ends with {@code _id} so pages never overlap. */
    private static Sort sortOf(MusicianSort sort) {
        Sort.Order primary = switch (sort) {
            case NAME -> Sort.Order.asc("nameNormalized");
            case LIKES -> Sort.Order.desc("likesCount");
            case RECENT -> Sort.Order.desc("publishedAt");
        };
        return Sort.by(primary, Sort.Order.asc("id"));
    }
}
