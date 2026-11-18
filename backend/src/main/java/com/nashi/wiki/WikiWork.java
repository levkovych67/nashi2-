package com.nashi.wiki;

/**
 * A notable work of an artist (an album), as collected from Wikidata.
 *
 * @param wikidataId  QID of the work
 * @param imageUrl    small image for lists
 * @param fullImageUrl full-size image
 * @param articleUrl  article about the work
 */
public record WikiWork(String wikidataId, String title, Integer year, String imageUrl, String fullImageUrl,
                       WikiImageCredit imageCredit, String articleUrl) {
}
