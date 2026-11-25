package com.nashi.wiki;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of a Wikidata item the import uses. Lists keep preferred statements first; deprecated ones are dropped.
 *
 * @param socialIds values keyed by property: P856 website, P2003 Instagram, P2013 Facebook, P2397 YouTube channel,
 *                  P1902 Spotify artist, P3040 SoundCloud, plus P2002, P7085, P3789, P2850, P2722, P3283, P1953,
 *                  P434, P3192, P2963, P648, P2042, P4613 (ESU), P13437 (Chtyvo) and P6002 (WikiArt)
 * @param occupations P106, preferred first
 * @param birthDate P569, deathDate P570 (best statement, cut to its precision)
 * @param movements P135
 * @param notableWorks P800
 * @param commonsCategory P373
 * @param wikisourceTitle title of the ukwikisource sitelink
 * @param partOf    current administrative parents (P131 without an end date)
 * @param awards    P166, awards and honorary titles received
 * @param countries P17 (best statements); for an award, the state that gives it
 * @param wholes    P361 (part of); for the degree of an order, the order
 * @param conferredBy P1027 (best statements); for an award, who gives it
 */
public record WikiEntity(
        String qid,
        String labelUk,
        String labelEn,
        String ukTitle,
        Set<String> instanceOf,
        Set<String> citizenships,
        List<String> birthPlaces,
        List<String> formationPlaces,
        List<String> residences,
        List<String> workPlaces,
        Integer inception,
        Integer dissolved,
        Integer workStart,
        Integer workEnd,
        String imageFile,
        List<String> genres,
        Map<String, String> socialIds,
        Double lat,
        Double lon,
        List<String> partOf,
        List<String> occupations,
        WikiDate birthDate,
        WikiDate deathDate,
        List<String> movements,
        List<String> notableWorks,
        String commonsCategory,
        String wikisourceTitle,
        List<String> awards,
        List<String> countries,
        List<String> wholes,
        List<String> conferredBy) {
}
