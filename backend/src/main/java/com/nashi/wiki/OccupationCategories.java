package com.nashi.wiki;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a Wikidata occupation (P106 value) to the kind of artist it makes a person. The map comes from the occupation
 * tree: every subclass ({@code wdt:P279*}) of the roots below. The QIDs were checked against the live API on
 * 2026-11-07.
 */
public final class OccupationCategories {

    /** Musician, singer, composer. */
    static final List<String> MUSICIAN_ROOTS = List.of("Q639669", "Q177220", "Q36834");

    private final Map<String, WikiCategory> byOccupation;

    public OccupationCategories(Map<String, WikiCategory> byOccupation) {
        this.byOccupation = Map.copyOf(byOccupation);
    }

    /** Every occupation of the musician tree is a musician. */
    static OccupationCategories fromMusicianTree(Collection<String> musician) {
        Map<String, WikiCategory> byOccupation = new HashMap<>();
        musician.forEach(occupation -> byOccupation.putIfAbsent(occupation, WikiCategory.MUSICIAN));
        return new OccupationCategories(byOccupation);
    }

    /** Kinds in the order their occupations first appear, each once; occupations outside the tree are ignored. */
    public List<WikiCategory> categoriesOf(List<String> occupations) {
        Set<WikiCategory> categories = new LinkedHashSet<>();
        for (String occupation : occupations) {
            WikiCategory category = byOccupation.get(occupation);
            if (category != null) {
                categories.add(category);
            }
        }
        return List.copyOf(categories);
    }

    int size() {
        return byOccupation.size();
    }
}
