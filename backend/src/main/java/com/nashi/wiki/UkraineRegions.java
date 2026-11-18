package com.nashi.wiki;

import com.nashi.geo.Region;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wikidata ids of Ukraine's 24 oblasts, the Autonomous Republic of Crimea, Kyiv and Sevastopol, one per region.
 * Kyiv ({@code Q1899}) and Sevastopol ({@code Q7525}) are regions of their own ({@link Region#KYIV_CITY},
 * {@link Region#SEVASTOPOL_CITY}), not parts of the Kyiv oblast and Crimea as in V1.
 */
public final class UkraineRegions {

    private static final Map<Region, String> QID_BY_REGION = new EnumMap<>(Map.ofEntries(
            Map.entry(Region.CRIMEA, "Q756294"),
            Map.entry(Region.VINNYTSIA, "Q166709"),
            Map.entry(Region.VOLYN, "Q167859"),
            Map.entry(Region.DNIPROPETROVSK, "Q170672"),
            Map.entry(Region.DONETSK, "Q2012050"),
            Map.entry(Region.ZHYTOMYR, "Q40637"),
            Map.entry(Region.ZAKARPATTIA, "Q170213"),
            Map.entry(Region.ZAPORIZHZHIA, "Q171334"),
            Map.entry(Region.IVANO_FRANKIVSK, "Q178269"),
            Map.entry(Region.KYIV, "Q170036"),
            Map.entry(Region.KIROVOHRAD, "Q180981"),
            Map.entry(Region.LUHANSK, "Q171965"),
            Map.entry(Region.LVIV, "Q164193"),
            Map.entry(Region.MYKOLAIV, "Q181633"),
            Map.entry(Region.ODESA, "Q171852"),
            Map.entry(Region.POLTAVA, "Q169501"),
            Map.entry(Region.RIVNE, "Q174187"),
            Map.entry(Region.SUMY, "Q170446"),
            Map.entry(Region.TERNOPIL, "Q173407"),
            Map.entry(Region.KHARKIV, "Q170666"),
            Map.entry(Region.KHERSON, "Q163271"),
            Map.entry(Region.KHMELNYTSKYI, "Q171331"),
            Map.entry(Region.CHERKASY, "Q161808"),
            Map.entry(Region.CHERNIVTSI, "Q168856"),
            Map.entry(Region.CHERNIHIV, "Q167874"),
            Map.entry(Region.KYIV_CITY, "Q1899"),
            Map.entry(Region.SEVASTOPOL_CITY, "Q7525")));

    private static final Map<String, Region> BY_QID = new HashMap<>();

    static {
        QID_BY_REGION.forEach((region, qid) -> {
            if (BY_QID.put(qid, region) != null) {
                throw new IllegalStateException("Duplicate region QID " + qid);
            }
        });
    }

    private static final List<Region> CITIES = List.of(Region.KYIV_CITY, Region.SEVASTOPOL_CITY);

    private UkraineRegions() {
    }

    public static Optional<Region> fromQid(String qid) {
        return Optional.ofNullable(BY_QID.get(qid));
    }

    public static String qidOf(Region region) {
        return QID_BY_REGION.get(region);
    }

    /** The 24 oblasts and Crimea (everything except Kyiv and Sevastopol), in {@link Region} order. */
    public static List<String> listOblastQids() {
        return QID_BY_REGION.entrySet().stream()
                .filter(e -> !CITIES.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /** Kyiv and Sevastopol, in {@link Region} order. */
    public static List<String> listCityQids() {
        return CITIES.stream().map(QID_BY_REGION::get).toList();
    }

    static int size() {
        return QID_BY_REGION.size();
    }

    static Collection<Region> regions() {
        return BY_QID.values();
    }
}
