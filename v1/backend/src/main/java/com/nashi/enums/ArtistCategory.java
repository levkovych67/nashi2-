package com.nashi.enums;

/**
 * What kind of artist this is. A person can have several, e.g. a poet who also painted.
 * Artists added through the form have none stored; see {@link com.nashi.util.ArtistFacts#categoriesOf}.
 */
public enum ArtistCategory {
    MUSICIAN,
    BAND,
    VISUAL_ARTIST,
    WRITER
}
