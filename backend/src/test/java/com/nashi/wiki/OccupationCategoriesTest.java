package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nashi.wiki.WikiCategory.BAND;
import static com.nashi.wiki.WikiCategory.MUSICIAN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupationCategoriesTest {

    private static final String SINGER = "Q177220";
    private static final String COMPOSER = "Q36834";
    private static final String POLITICIAN = "Q82955";

    private final OccupationCategories kinds = OccupationCategories.fromMusicianTree(List.of("Q639669", SINGER, COMPOSER));

    @Test
    void musicianOccupationIsMusician() {
        assertEquals(List.of(MUSICIAN), kinds.categoriesOf(List.of(SINGER, COMPOSER)));
    }

    @Test
    void unknownOccupationIgnored() {
        assertEquals(List.of(MUSICIAN), kinds.categoriesOf(List.of(POLITICIAN, SINGER)));
        assertEquals(List.of(), kinds.categoriesOf(List.of(POLITICIAN)));
    }

    @Test
    void emptyGivesEmpty() {
        assertEquals(List.of(), kinds.categoriesOf(List.of()));
        assertEquals(List.of(), new OccupationCategories(Map.of()).categoriesOf(List.of(SINGER)));
    }

    @Test
    void mapGivenToTheConstructorIsUsedAsIs() {
        OccupationCategories direct = new OccupationCategories(Map.of(SINGER, BAND, COMPOSER, MUSICIAN));

        assertEquals(List.of(BAND, MUSICIAN), direct.categoriesOf(List.of(SINGER, POLITICIAN, COMPOSER)));
    }

    @Test
    void onlyTheMusicianRootsRemain() {
        assertEquals(List.of("Q639669", "Q177220", "Q36834"), OccupationCategories.MUSICIAN_ROOTS);
    }
}
