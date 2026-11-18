package com.nashi.wiki;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SPARQL queries that list import candidates and their works. Asking for people by an occupation root with all its
 * subclasses ({@code wdt:P106/wdt:P279*}) times out on the public endpoint (60 s), so the occupation trees are fetched
 * first ({@link #occupationTree}) and people are asked for a chunk of occupations at a time
 * ({@link #peopleWithOccupations}), once per rule (citizen of / born in Ukraine). Ensembles are one query. Albums are
 * asked for a chunk of artists at a time ({@link #albums}).
 */
public final class WikiQueries {

    static final String CITIZEN = "?item wdt:P27 wd:Q212. ";
    static final String BORN = "?item wdt:P19/wdt:P17 wd:Q212. ";

    private static final String UK_ARTICLE =
            "?a schema:about ?item; schema:isPartOf <https://uk.wikipedia.org/>; schema:name ?title. ";
    private static final String NOT_RUSSIAN = "FILTER NOT EXISTS { ?item wdt:P27 wd:Q159 } ";
    private static final String WORK_ARTICLE = "OPTIONAL { ?article schema:about ?work; "
            + "schema:isPartOf <https://uk.wikipedia.org/>; schema:name ?ukTitle. } ";
    /** uk label, else en; the service answers with the QID when there is neither. */
    private static final String WORK_LABEL =
            "SERVICE wikibase:label { bd:serviceParam wikibase:language \"uk,en\". ?work rdfs:label ?title. } ";

    private WikiQueries() {
    }

    /** The root and all its subclasses, without the excluded subtrees; binds {@code ?occ}. */
    static String occupationTree(String root, List<String> excludedRoots) {
        return "SELECT DISTINCT ?occ WHERE { ?occ wdt:P279* wd:" + root + ". "
                + excludedRoots.stream()
                        .map(excluded -> "MINUS { ?occ wdt:P279* wd:" + excluded + " } ")
                        .collect(Collectors.joining())
                + "}";
    }

    /** People with any of the given occupations exactly (no subclasses: the list is a chunk of a tree). */
    static String peopleWithOccupations(List<String> occupations, String ukraineRule) {
        return "SELECT DISTINCT ?item ?title WHERE { "
                + values("occ", occupations)
                + "?item wdt:P106 ?occ; wdt:P31 wd:Q5. "
                + ukraineRule
                + NOT_RUSSIAN
                + UK_ARTICLE
                + "}";
    }

    static String ensembles() {
        return "SELECT DISTINCT ?item ?title WHERE { "
                + "?item wdt:P31/wdt:P279* wd:Q2088357. "
                + "{ ?item wdt:P495 wd:Q212 } UNION { ?item wdt:P740/wdt:P17 wd:Q212 } "
                + UK_ARTICLE
                + "}";
    }

    /**
     * Albums (P31 album Q482994 or a subclass) whose performer (P175) is one of the artists, with a label, the year of
     * P577 and the uk.wikipedia article; one row per artist, album and date. Binds {@code ?creator} and {@code ?work}
     * for {@link SparqlParser#parseWorks}.
     */
    static String albums(List<String> artists) {
        return "SELECT DISTINCT ?creator ?work ?title ?year ?ukTitle WHERE { "
                + values("creator", artists)
                + "?work wdt:P175 ?creator; wdt:P31/wdt:P279* wd:Q482994. "
                + "OPTIONAL { ?work wdt:P577 ?date. } BIND(YEAR(?date) AS ?year) "
                + WORK_ARTICLE
                + WORK_LABEL
                + "}";
    }

    private static String values(String var, List<String> ids) {
        return "VALUES ?" + var + " { " + ids.stream().map(id -> "wd:" + id + " ").collect(Collectors.joining()) + "} ";
    }
}
