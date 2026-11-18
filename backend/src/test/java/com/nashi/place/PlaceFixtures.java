package com.nashi.place;

import com.nashi.artist.Names;
import com.nashi.geo.Region;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * Six places of the seed for the place ITs. They share the database with every other IT, so each test class clears
 * {@code places} before and after it runs (documents only: the indexes from {@code MongoIndexes} stay).
 */
final class PlaceFixtures {

    static final Place KYIV = place("Q1899", "Київ", PlaceType.CITY, Region.KYIV_CITY, 50.45, 30.523611111, 848.0,
            2_952_301L);
    /** No population: sorts after every place with one. */
    static final Place KYIEVO_OLEKSANDRIVSKE = place("Q4220129", "Києво-Олександрівське", PlaceType.VILLAGE,
            Region.MYKOLAIV, 47.255555555, 31.363611111, 0.33, null);
    static final Place KAMIANETS = place("Q193965", "Кам'янець-Подільський", PlaceType.CITY, Region.KHMELNYTSKYI,
            48.680555555, 26.580555555, 27.871, 96_896L);
    static final Place BRODY = place("Q465104", "Броди", PlaceType.CITY, Region.LVIV, 50.078055555, 25.154166666,
            8.67, 23_454L);
    static final Place LVIV = place("Q36036", "Львів", PlaceType.CITY, Region.LVIV, 49.841952, 24.0315921, 182.0,
            717_273L);
    static final Place PODILSKE = place("Q4368083", "Подільське", PlaceType.VILLAGE, Region.KHMELNYTSKYI, 48.7,
            26.966666666, 2.559, null);

    static final List<Place> ALL = List.of(KYIV, KYIEVO_OLEKSANDRIVSKE, KAMIANETS, BRODY, LVIV, PODILSKE);

    private PlaceFixtures() {
    }

    static Place place(String id, String name, PlaceType type, Region region, double lat, double lon,
                       Double areaKm2, Long population) {
        return new Place(id, name, Names.normalize(name), type, region, new GeoJsonPoint(lon, lat), areaKm2,
                population);
    }

    static void clear(MongoTemplate mongo) {
        mongo.remove(new Query(), Place.class);
    }

    static void insertAll(MongoTemplate mongo) {
        mongo.insertAll(ALL);
    }
}
