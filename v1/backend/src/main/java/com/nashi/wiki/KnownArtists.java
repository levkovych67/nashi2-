package com.nashi.wiki;

import java.util.Set;

/**
 * What is already in the database.
 *
 * @param manualNames normalized names of artists added through the form (no Wikidata id), except rejected ones
 */
public record KnownArtists(Set<String> wikidataIds, Set<String> wikiTitles, Set<String> manualNames) {
}
