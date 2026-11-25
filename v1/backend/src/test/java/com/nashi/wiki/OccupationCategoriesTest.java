package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nashi.enums.ArtistCategory.MUSICIAN;
import static com.nashi.enums.ArtistCategory.VISUAL_ARTIST;
import static com.nashi.enums.ArtistCategory.WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupationCategoriesTest {

    private static final String SINGER = "Q177220";
    private static final String PAINTER = "Q1028181";
    private static final String POET = "Q49757";
    private static final String POLITICIAN = "Q82955";
    /** In both the visual-artist and the writer tree on Wikidata. */
    private static final String GRAPHIC_NOVELIST = "Q107436166";

    private final OccupationCategories kinds = OccupationCategories.fromTrees(
            List.of("Q639669", SINGER),
            List.of("Q3391743", PAINTER, GRAPHIC_NOVELIST),
            List.of("Q36180", POET, GRAPHIC_NOVELIST));

    @Test
    void poetPainterIsWriterThenVisual() {
        assertEquals(List.of(WRITER, VISUAL_ARTIST), kinds.categoriesOf(List.of(POET, PAINTER)));
    }

    @Test
    void unknownOccupationIgnored() {
        assertEquals(List.of(VISUAL_ARTIST), kinds.categoriesOf(List.of(POLITICIAN, PAINTER)));
    }

    @Test
    void duplicatesCollapsed() {
        assertEquals(List.of(VISUAL_ARTIST, WRITER), kinds.categoriesOf(List.of(PAINTER, POET, PAINTER, "Q36180")));
    }

    @Test
    void emptyGivesEmpty() {
        assertEquals(List.of(), kinds.categoriesOf(List.of()));
        assertEquals(List.of(), new OccupationCategories(Map.of()).categoriesOf(List.of(POET)));
    }

    @Test
    void occupationInTwoTreesGoesToFirstKind() {
        assertEquals(List.of(VISUAL_ARTIST), kinds.categoriesOf(List.of(GRAPHIC_NOVELIST)));

        OccupationCategories overlapping = OccupationCategories.fromTrees(
                List.of("Q1"), List.of("Q1", "Q2"), List.of("Q1", "Q2", "Q3"));
        assertEquals(List.of(MUSICIAN, VISUAL_ARTIST, WRITER), overlapping.categoriesOf(List.of("Q1", "Q2", "Q3")));
        assertEquals(List.of(WRITER, VISUAL_ARTIST), overlapping.categoriesOf(List.of("Q3", "Q2")));
    }

    @Test
    void mapGivenToTheConstructorIsUsedAsIs() {
        OccupationCategories direct = new OccupationCategories(Map.of(POET, WRITER, SINGER, MUSICIAN));

        assertEquals(List.of(MUSICIAN, WRITER), direct.categoriesOf(List.of(SINGER, PAINTER, POET)));
    }

    @Test
    void rootsAndExcludedSubtreesAreTheVerifiedOnes() {
        assertEquals(List.of("Q639669", "Q177220", "Q36834"), OccupationCategories.MUSICIAN_ROOTS);
        assertEquals("Q3391743", OccupationCategories.VISUAL_ROOT);
        // filmmaker, stand-up comedian, make-up artist, cartographer
        assertEquals(List.of("Q1414443", "Q18545066", "Q935666", "Q1734662"), OccupationCategories.VISUAL_EXCLUDED);
        assertEquals("Q36180", OccupationCategories.WRITER_ROOT);
        // columnist, correspondent
        assertEquals(List.of("Q1086863", "Q3589290"), OccupationCategories.WRITER_EXCLUDED);
    }
}
