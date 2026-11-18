package com.nashi.place;

import com.nashi.artist.Names;
import com.nashi.geo.Region;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Lookups in the gazetteer for imports, submissions and the map. */
@Component
public class PlaceStore {

    private final MongoTemplate mongo;

    public PlaceStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Optional<Place> find(String placeId) {
        return Optional.ofNullable(mongo.findById(placeId, Place.class));
    }

    /**
     * The place with this QID; when the seed does not have it, a new {@link PlaceType#OTHER} place from the given
     * data. An existing place is returned unchanged. Atomic: concurrent calls for one QID create one place.
     */
    public Place ensure(String qid, String name, Region region, double lat, double lon) {
        Update insertOnly = new Update()
                .setOnInsert("name", name)
                .setOnInsert("nameNormalized", Names.normalize(name))
                .setOnInsert("type", PlaceType.OTHER)
                .setOnInsert("region", region)
                .setOnInsert("point", new GeoJsonPoint(lon, lat));
        return mongo.findAndModify(Query.query(Criteria.where("_id").is(qid)), insertOnly,
                FindAndModifyOptions.options().upsert(true).returnNew(true), Place.class);
    }

    /** The place closest to the point, if one lies within {@code maxMeters}. Needs {@code places.point_2dsphere}. */
    public Optional<Place> nearest(double lat, double lon, double maxMeters) {
        Query query = Query.query(Criteria.where("point").nearSphere(new GeoJsonPoint(lon, lat)).maxDistance(maxMeters))
                .limit(1);
        return Optional.ofNullable(mongo.findOne(query, Place.class));
    }
}
