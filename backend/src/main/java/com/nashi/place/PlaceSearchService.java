package com.nashi.place;

import com.nashi.artist.Names;
import com.nashi.geo.Region;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Autocomplete over {@code places} ({@code docs/02-contracts.md} 2.9): the normalized query must be the start of a
 * word of the normalized name (a word starts the name or follows a space or a hyphen), so «под» finds
 * «Кам'янець-Подільський» but «ьвів» does not find «Львів». Most populous first; places without a population last.
 */
@Service
public class PlaceSearchService {

    private static final Sort ORDER = Sort.by(Sort.Order.desc("population"), Sort.Order.asc("name"),
            Sort.Order.asc("_id"));

    private final MongoTemplate mongo;

    public PlaceSearchService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * @param q      raw user input; callers validate that its normalized form has at least two characters
     * @param region only places of this region, or all when {@code null}
     * @param limit  most suggestions to return
     */
    public List<PlaceSuggestion> search(String q, @Nullable Region region, int limit) {
        Criteria criteria = Criteria.where("nameNormalized").regex(wordPrefix(Names.normalize(q)));
        if (region != null) {
            criteria = criteria.and("region").is(region);
        }
        Query query = Query.query(criteria).with(ORDER).limit(limit);
        return mongo.find(query, Place.class).stream().map(PlaceSuggestion::of).toList();
    }

    static Pattern wordPrefix(String normalized) {
        return Pattern.compile("(^|[ \\-])" + Pattern.quote(normalized));
    }
}
