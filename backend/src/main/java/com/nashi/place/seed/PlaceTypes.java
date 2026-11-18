package com.nashi.place.seed;

import com.nashi.place.PlaceType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wikidata {@code P31} classes of Ukrainian settlements the seed keeps, with the {@link PlaceType} of each, and the
 * classes that mark a place as gone.
 * <p>
 * Derived on 2026-11-18 from {@code ./gradlew placesSeed -Ptypes}: the {@code P31} values of everything with a
 * coordinate inside each of the 27 regions that are {@code Q486972} (human settlement) or a subclass of it. The
 * counts in the comments are places per class (a place can have several). Kept: cities, towns and urban-type
 * settlements, villages, rural settlements, khutirs and hamlets, including the classes Wikidata uses for Crimea under
 * occupation. Left out: the generic «населений пункт» (Q486972, 1348; Q12051488, 24), parts of cities (raions,
 * microdistricts, neighbourhoods, quarters), municipalities ({@code сільське/міське поселення Росії}), historical and
 * archaeological sites, camps, ghettos, colonies, monasteries, slobodas and dacha settlements.
 */
public final class PlaceTypes {

    /** One class: its QID, our type and its Wikidata label (uk | en), for review. */
    record Entry(String qid, PlaceType type, String label) {
    }

    /** Comments: how many places had the class in the {@code --types} run. */
    static final List<Entry> TABLE = List.of(
            new Entry("Q12131624", PlaceType.CITY, "місто України | city in Ukraine"), // 386
            new Entry("Q5123999", PlaceType.CITY,
                    "місто обласного значення України | city of regional significance of Ukraine"), // 101
            new Entry("Q7930989", PlaceType.CITY, "місто/містечко | city or town"), // 78
            new Entry("Q515", PlaceType.CITY, "місто | city"), // 40
            new Entry("Q12131640", PlaceType.CITY, "місто районного значення | city of district significance"), // 16
            new Entry("Q1549591", PlaceType.CITY, "місто з населенням понад 100 тис. осіб | big city"), // 16
            new Entry("Q12134916", PlaceType.CITY, "обласний центр | centre of oblast"), // 7
            new Entry("Q106389302", PlaceType.CITY, "місто в Росії | city/town in Russia"), // 1, in Crimea
            new Entry("Q121366249", PlaceType.TOWN, "селище України | rural settlement in Ukraine"), // 953
            new Entry("Q4100864", PlaceType.TOWN,
                    "селище міського типу України | urban-type settlement in Ukraine"), // 533
            new Entry("Q2514025", PlaceType.TOWN, "селище | posyolok"), // 121
            new Entry("Q2989457", PlaceType.TOWN, "селище міського типу | urban-type settlement"), // 56
            new Entry("Q15078955", PlaceType.TOWN, "смт Росії | urban-type settlement in Russia"), // 39, in Crimea
            new Entry("Q3957", PlaceType.TOWN, "містечко | town"), // 6
            new Entry("Q21672098", PlaceType.VILLAGE, "село України | village of Ukraine"), // 27762
            new Entry("Q532", PlaceType.VILLAGE, "село | village"), // 1730
            new Entry("Q41501164", PlaceType.VILLAGE, "село Криму | village of Crimea"), // 195
            new Entry("Q2023000", PlaceType.VILLAGE, "хутір | khutor"), // 177
            new Entry("Q5084", PlaceType.VILLAGE, "присілок | hamlet"), // 19
            new Entry("Q4379239", PlaceType.VILLAGE, "присілок | hamlet (Russia)")); // 6

    /** A place with any of these {@code P31} values no longer exists, whatever else it is typed as. */
    private static final Set<String> EXCLUDED = Set.of(
            "Q22674925",  // колишній населений пункт | former settlement (570)
            "Q21507948",  // колишнє село | former village (84)
            "Q350895",    // пустовщина | abandoned village (11)
            "Q74047",     // місто-привид | ghost town (7)
            "Q2974842",   // lost city (2)
            "Q20616831"); // submerged settlement (1)

    private static final Map<String, PlaceType> BY_QID = new LinkedHashMap<>();

    static {
        for (Entry e : TABLE) {
            if (BY_QID.put(e.qid(), e.type()) != null) {
                throw new IllegalStateException("Duplicate place type " + e.qid());
            }
        }
    }

    private PlaceTypes() {
    }

    /** {@code P31} values that drop a place even when it also has a kept type. */
    public static Set<String> excludedQids() {
        return EXCLUDED;
    }

    public static Optional<PlaceType> of(String qid) {
        return Optional.ofNullable(BY_QID.get(qid));
    }

    /** The QIDs to ask Wikidata for, in table order. */
    public static Set<String> qids() {
        return BY_QID.keySet();
    }
}
