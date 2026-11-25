package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WikidataEntityParserTest {

    private final Map<String, WikiEntity> artists = WikidataEntityParser.parse(Fixtures.read("entities-artists.json"));
    private final Map<String, WikiEntity> kinds = WikidataEntityParser.parse(Fixtures.read("entities-kinds.json"));
    private final Map<String, WikiEntity> places = WikidataEntityParser.parse(Fixtures.read("entities-places.json"));
    private final Map<String, WikiEntity> awarded = WikidataEntityParser.parse(Fixtures.read("entities-awarded.json"));
    private final Map<String, WikiEntity> awards = WikidataEntityParser.parse(Fixtures.read("entities-awards.json"));

    @Test
    void readsAClassicWhoseCitizenshipIsTheRussianEmpire() {
        WikiEntity lysenko = artists.get("Q172425");

        assertEquals("Микола Лисенко", lysenko.labelUk());
        assertEquals("Лисенко Микола Віталійович", lysenko.ukTitle());
        assertTrue(lysenko.citizenships().contains("Q34266"));
        assertFalse(lysenko.citizenships().contains("Q212"));
        assertEquals(List.of("Q4149698"), lysenko.birthPlaces());
        assertEquals(List.of("Q1899"), lysenko.residences());
        assertEquals(1869, lysenko.workStart());
        assertEquals("Лисенко Микола (cropped).jpg", lysenko.imageFile());
        assertEquals(Map.of(), lysenko.socialIds());
    }

    @Test
    void readsRussianCitizenship() {
        assertTrue(artists.get("Q1980296").citizenships().contains("Q159"));
    }

    @Test
    void readsABand() {
        WikiEntity band = artists.get("Q1008489");

        assertEquals(java.util.Set.of("Q215380"), band.instanceOf());
        assertEquals(List.of("Q36036"), band.formationPlaces());
        assertEquals(1994, band.inception());
        assertEquals(4, band.genres().size());
        assertEquals("https://okeanelzy.com", band.socialIds().get("P856"));
        assertEquals("okeanelzy_official", band.socialIds().get("P2003"));
    }

    @Test
    void followsOnlyTheCurrentAdministrativeParent() {
        WikiEntity hrynky = places.get("Q4149698");

        assertEquals(List.of("Q103852125"), hrynky.partOf());
        assertEquals(49.4575, hrynky.lat(), 0.001);
        assertEquals(33.0301, hrynky.lon(), 0.001);
        assertEquals("Гриньки", hrynky.labelUk());
    }

    @Test
    void skipsEntitiesThatDoNotExist() {
        String json = "{\"entities\":{\"Q999999999\":{\"id\":\"Q999999999\",\"missing\":\"\"}}}";

        assertTrue(WikidataEntityParser.parse(json).isEmpty());
    }

    @Test
    void parsesOccupationsInOrder() {
        // statement order in the fixture: painter, poet, ..., writer (Q36180) later on
        List<String> occupations = kinds.get("Q134958").occupations();

        assertEquals(List.of("Q1028181", "Q49757", "Q4773904", "Q483501", "Q36180", "Q214917", "Q12347522",
                "Q12144794", "Q4964182", "Q69423232"), occupations);
        assertEquals(List.of("Q1028181", "Q3391743"), kinds.get("Q4082561").occupations());
        assertEquals(List.of(), kinds.get("Q1008489").occupations());
    }

    @Test
    void parsesBirthAndDeathDatesFromTheBestStatement() {
        // the preferred statements are Julian (1814-02-25, 1861-02-26) and are shown as Gregorian
        WikiEntity shevchenko = kinds.get("Q134958");

        assertEquals(new WikiDate("1814-03-09", 1814), shevchenko.birthDate());
        assertEquals(new WikiDate("1861-03-10", 1861), shevchenko.deathDate());
        assertEquals(new WikiDate("1900-12-08", 1900), kinds.get("Q4082561").birthDate());
        assertNull(kinds.get("Q2662517").deathDate());
        assertNull(kinds.get("Q1008489").birthDate());
    }

    @Test
    void parsesMovementsCommonsCategoryNotableWorks() {
        WikiEntity shevchenko = kinds.get("Q134958");

        assertEquals(List.of("Q37068"), shevchenko.movements());
        assertEquals("Taras Shevchenko", shevchenko.commonsCategory());
        assertEquals(List.of("Q2658157", "Q18551805", "Q9335228"), shevchenko.notableWorks());
        assertEquals(List.of("Q11816946"), kinds.get("Q298033").notableWorks());
        assertNull(kinds.get("Q1008489").wikisourceTitle());
        assertEquals(List.of(), kinds.get("Q4082561").notableWorks());
    }

    @Test
    void parsesUkWikisourceSitelink() {
        assertEquals("Автор:Тарас Шевченко", kinds.get("Q134958").wikisourceTitle());
        assertEquals("Шевченко Тарас Григорович", kinds.get("Q134958").ukTitle());
        assertNull(kinds.get("Q4082561").wikisourceTitle());
    }

    @Test
    void parsesNewLinkIdentifiers() {
        Map<String, String> jamala = kinds.get("Q2662517").socialIds();
        assertEquals("jamala", jamala.get("P2002"));
        assertEquals("4EXfia20rgVLyubQiYtOIC", jamala.get("P1902"));
        assertEquals("3d264dc4-b1f2-409f-8ab4-3d17d8b42b02", jamala.get("P434"));
        assertEquals("2494137", jamala.get("P1953"));

        Map<String, String> band = kinds.get("Q1008489").socialIds();
        assertEquals("okeanelzy", band.get("P2002"));
        assertEquals("87a417d3-f118-43e0-bece-3de1bd90fce0", band.get("P434"));
        assertEquals("125748", band.get("P1953"));

        assertEquals("OL57555A", kinds.get("Q134958").socialIds().get("P648"));
        assertEquals("135390", kinds.get("Q134958").socialIds().get("P2963"));
        assertEquals("kateryna-bilokur", kinds.get("Q4082561").socialIds().get("P2042"));
    }

    @Test
    void parsesEsuChtyvoAndWikiArtIds() {
        Map<String, String> bilokur = kinds.get("Q4082561").socialIds();
        assertEquals("40828", bilokur.get("P4613"));
        assertEquals("kateryna-bilokur", bilokur.get("P6002"));
        assertEquals("Ukrainka", kinds.get("Q298033").socialIds().get("P13437"));
        assertEquals("taras-shevchenko", kinds.get("Q134958").socialIds().get("P6002"));
    }

    @Test
    void parsesAwards() {
        // Yuriy Bogatikov: Honoured Artist of Ukraine, People's Artist of the USSR, the Russian Order of Friendship, …;
        // «Почесний кримчанин» is stated twice and kept once
        List<String> bogatikov = awarded.get("Q4089030").awards();
        assertEquals(List.of("Q2028948", "Q403569", "Q791135"), bogatikov.subList(0, 3));
        assertEquals(16, bogatikov.size());
        assertEquals(List.of("Q960966", "Q46666360", "Q33082912"), kinds.get("Q4082561").awards());
        assertEquals(List.of(), kinds.get("Q134958").awards());
    }

    @Test
    void parsesTheCountriesOfAnAward() {
        assertEquals(List.of("Q159"), awards.get("Q791135").countries());
        assertEquals(List.of("Q15180"), awards.get("Q403569").countries());
        // People's Artist of the RSFSR: the country is the USSR, the RSFSR is its administrative unit (P131)
        assertEquals(List.of("Q2184"), awards.get("Q47024").partOf());
        assertEquals(7, awards.get("Q783276").countries().size());
    }

    @Test
    void parsesPartOfAndConferredBy() {
        // the 2nd degree of the Order of St. Sergius is part of the order, which the Russian Orthodox Church confers
        assertEquals(List.of("Q3355529"), awards.get("Q29043608").wholes());
        assertEquals(List.of("Q60995"), awards.get("Q3355529").conferredBy());
        assertEquals(List.of(), awards.get("Q2268261").conferredBy());
        assertEquals(List.of("Q431668"), awards.get("Q16687981").wholes(), "a Nika category is part of the Nika");
    }
}
