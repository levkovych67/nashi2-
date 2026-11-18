package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiQueriesTest {

    private static final String NOT_RUSSIAN = "FILTER NOT EXISTS { ?item wdt:P27 wd:Q159 } ";
    private static final String UK_ARTICLE =
            "?a schema:about ?item; schema:isPartOf <https://uk.wikipedia.org/>; schema:name ?title. ";
    private static final String CITIZEN = "?item wdt:P27 wd:Q212. ";
    private static final String BORN = "?item wdt:P19/wdt:P17 wd:Q212. ";

    @Test
    void rulesAreCitizenAndBornInUkraine() {
        assertEquals(CITIZEN, WikiQueries.CITIZEN);
        assertEquals(BORN, WikiQueries.BORN);
    }

    @Test
    void eachPeopleQueryLeavesOutRussiansNeedsAUkArticleAndHasItsRule() {
        assertPeopleQuery(WikiQueries.peopleWithOccupations(List.of("Q1028181"), CITIZEN), CITIZEN);
        assertPeopleQuery(WikiQueries.peopleWithOccupations(List.of("Q1028181"), BORN), BORN);
    }

    @Test
    void occupationTreeHasAMinusPerExcludedRoot() {
        String query = WikiQueries.occupationTree("Q3391743", List.of("Q1414443", "Q18545066", "Q935666", "Q1734662"));

        assertEquals("SELECT DISTINCT ?occ WHERE { ?occ wdt:P279* wd:Q3391743. "
                + "MINUS { ?occ wdt:P279* wd:Q1414443 } "
                + "MINUS { ?occ wdt:P279* wd:Q18545066 } "
                + "MINUS { ?occ wdt:P279* wd:Q935666 } "
                + "MINUS { ?occ wdt:P279* wd:Q1734662 } }", query);
        assertEquals(4, query.split("MINUS", -1).length - 1);
    }

    @Test
    void occupationTreeWithoutExclusionsHasNoMinus() {
        String query = WikiQueries.occupationTree("Q639669", List.of());

        assertEquals("SELECT DISTINCT ?occ WHERE { ?occ wdt:P279* wd:Q639669. }", query);
        assertFalse(query.contains("MINUS"));
    }

    @Test
    void peopleWithOccupationsListsEveryOccupationInValues() {
        String query = WikiQueries.peopleWithOccupations(List.of("Q1028181", "Q1281618", "Q33231"), BORN);

        assertEquals("SELECT DISTINCT ?item ?title WHERE { "
                + "VALUES ?occ { wd:Q1028181 wd:Q1281618 wd:Q33231 } "
                + "?item wdt:P106 ?occ; wdt:P31 wd:Q5. "
                + BORN + NOT_RUSSIAN + UK_ARTICLE + "}", query);
    }

    @Test
    void ensembleQueryIsUnchanged() {
        assertEquals("SELECT DISTINCT ?item ?title WHERE { ?item wdt:P31/wdt:P279* wd:Q2088357. "
                + "{ ?item wdt:P495 wd:Q212 } UNION { ?item wdt:P740/wdt:P17 wd:Q212 } "
                + "?a schema:about ?item; schema:isPartOf <https://uk.wikipedia.org/>; schema:name ?title. }",
                WikiQueries.ensembles());
    }

    @Test
    void albumQueryAsksForAlbumsOfTheCreatorsWithYearArticleAndLabel() {
        assertEquals("SELECT DISTINCT ?creator ?work ?title ?year ?ukTitle WHERE { "
                + "VALUES ?creator { wd:Q1008489 wd:Q2662517 } "
                + "?work wdt:P175 ?creator; wdt:P31/wdt:P279* wd:Q482994. "
                + "OPTIONAL { ?work wdt:P577 ?date. } BIND(YEAR(?date) AS ?year) "
                + "OPTIONAL { ?article schema:about ?work; schema:isPartOf <https://uk.wikipedia.org/>; "
                + "schema:name ?ukTitle. } "
                + "SERVICE wikibase:label { bd:serviceParam wikibase:language \"uk,en\". ?work rdfs:label ?title. } }",
                WikiQueries.albums(List.of("Q1008489", "Q2662517")));
    }

    private static void assertPeopleQuery(String query, String rule) {
        assertTrue(query.contains(NOT_RUSSIAN), query);
        assertTrue(query.contains(UK_ARTICLE), query);
        assertTrue(query.contains(rule), query);
        assertTrue(query.contains("wdt:P31 wd:Q5. "), query);
    }
}
