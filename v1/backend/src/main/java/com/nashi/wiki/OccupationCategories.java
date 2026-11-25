package com.nashi.wiki;

import com.nashi.enums.ArtistCategory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a Wikidata occupation (P106 value) to the kind of artist it makes a person. The map comes from the occupation
 * trees: every subclass ({@code wdt:P279*}) of the roots below, minus the excluded subtrees. The QIDs and labels were
 * checked against the live API on 2026-11-07.
 */
public final class OccupationCategories {

    /** Musician, singer, composer. */
    static final List<String> MUSICIAN_ROOTS = List.of("Q639669", "Q177220", "Q36834");

    /** Visual artist (візуальний митець). */
    static final String VISUAL_ROOT = "Q3391743";

    /**
     * Subtrees of visual artist that are not visual artists for us: filmmaker (кінематографіст; it covers film
     * director, cinematographer, camera operator, film editor, production designer and animator), stand-up comedian
     * (стендап-комік), make-up artist (візажист) and cartographer (картограф).
     */
    static final List<String> VISUAL_EXCLUDED = List.of("Q1414443", "Q18545066", "Q935666", "Q1734662");

    /** Writer (письменник). Lyricist Q822146 (поет-пісняр) stays in: a lyricist is a poet. */
    static final String WRITER_ROOT = "Q36180";

    /** Columnist (колумніст) and correspondent (Q3589290, "author of (literary) letters", no uk label). */
    static final List<String> WRITER_EXCLUDED = List.of("Q1086863", "Q3589290");

    private final Map<String, ArtistCategory> byOccupation;

    public OccupationCategories(Map<String, ArtistCategory> byOccupation) {
        this.byOccupation = Map.copyOf(byOccupation);
    }

    /** An occupation in more than one tree goes to the first kind in the order musician, visual artist, writer. */
    static OccupationCategories fromTrees(Collection<String> musician, Collection<String> visual,
                                          Collection<String> writer) {
        Map<String, ArtistCategory> byOccupation = new HashMap<>();
        musician.forEach(occupation -> byOccupation.putIfAbsent(occupation, ArtistCategory.MUSICIAN));
        visual.forEach(occupation -> byOccupation.putIfAbsent(occupation, ArtistCategory.VISUAL_ARTIST));
        writer.forEach(occupation -> byOccupation.putIfAbsent(occupation, ArtistCategory.WRITER));
        return new OccupationCategories(byOccupation);
    }

    /** Kinds in the order their occupations first appear, each once; occupations outside every tree are ignored. */
    public List<ArtistCategory> categoriesOf(List<String> occupations) {
        Set<ArtistCategory> categories = new LinkedHashSet<>();
        for (String occupation : occupations) {
            ArtistCategory category = byOccupation.get(occupation);
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
