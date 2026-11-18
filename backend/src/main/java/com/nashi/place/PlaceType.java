package com.nashi.place;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kind of settlement ({@code docs/02-contracts.md} section 2): {@code CITY} (місто), {@code TOWN} (селище, смт),
 * {@code VILLAGE} (село, селище сільського типу, хутір), {@code OTHER} (a place the seed does not know, added by an
 * import). Declared from the highest rank to the lowest: when Wikidata gives several kinds, the first one wins.
 */
@Schema(enumAsRef = true)
public enum PlaceType {
    CITY, TOWN, VILLAGE, OTHER
}
