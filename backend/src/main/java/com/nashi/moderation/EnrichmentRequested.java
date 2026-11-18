package com.nashi.moderation;

/** Published after an approved submission became an artist and it named a Wikipedia article; T19 enriches it. */
public record EnrichmentRequested(String artistId, String wikipediaUrl) {
}
