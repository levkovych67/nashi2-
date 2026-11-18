package com.nashi.like;

import com.nashi.api.NotFoundException;
import com.nashi.artist.Artist;
import com.nashi.artist.ArtistStatus;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Atomic {@code $inc} on {@code likesCount}. Deliberately not a save: it leaves {@code updatedAt} alone and
 * publishes no {@code ArtistChangedEvent}, so likes never invalidate the map snapshot.
 */
@Service
public class LikeService {

    private final MongoTemplate mongo;

    public LikeService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public long add(String artistId, int count) {
        Artist updated = mongo.findAndModify(
                query(where("_id").is(artistId).and("status").is(ArtistStatus.PUBLISHED)),
                new Update().inc("likesCount", count),
                FindAndModifyOptions.options().returnNew(true),
                Artist.class);
        if (updated == null) {
            throw new NotFoundException("musician");
        }
        return updated.getLikesCount();
    }
}
