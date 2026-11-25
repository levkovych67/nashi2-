package com.nashi.wiki;

import com.nashi.enums.Region;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/** Wikidata ids of Ukraine's oblasts, Crimea, Kyiv and Sevastopol, mapped to our regions. */
public final class UkraineRegions {

    private static final Map<String, Region> BY_QID = Map.ofEntries(
            entry("Q178269", Region.IVANO_FRANKIVSK),
            entry("Q756294", Region.CRIMEA),
            entry("Q167859", Region.VOLYN),
            entry("Q166709", Region.VINNYTSIA),
            entry("Q170672", Region.DNIPROPETROVSK),
            entry("Q2012050", Region.DONETSK),
            entry("Q40637", Region.ZHYTOMYR),
            entry("Q170213", Region.ZAKARPATTIA),
            entry("Q171334", Region.ZAPORIZHZHIA),
            entry("Q1899", Region.KYIV),
            entry("Q170036", Region.KYIV),
            entry("Q180981", Region.KIROVOHRAD),
            entry("Q171965", Region.LUHANSK),
            entry("Q164193", Region.LVIV),
            entry("Q181633", Region.MYKOLAIV),
            entry("Q171852", Region.ODESA),
            entry("Q169501", Region.POLTAVA),
            entry("Q174187", Region.RIVNE),
            entry("Q7525", Region.CRIMEA),
            entry("Q170446", Region.SUMY),
            entry("Q173407", Region.TERNOPIL),
            entry("Q170666", Region.KHARKIV),
            entry("Q163271", Region.KHERSON),
            entry("Q171331", Region.KHMELNYTSKYI),
            entry("Q161808", Region.CHERKASY),
            entry("Q168856", Region.CHERNIVTSI),
            entry("Q167874", Region.CHERNIHIV));

    private UkraineRegions() {
    }

    public static Optional<Region> fromQid(String qid) {
        return Optional.ofNullable(BY_QID.get(qid));
    }

    static int size() {
        return BY_QID.size();
    }

    static Collection<Region> regions() {
        return BY_QID.values();
    }
}
