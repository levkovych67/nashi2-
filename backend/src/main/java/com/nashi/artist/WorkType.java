package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kind of a release or composition. */
@Schema(enumAsRef = true)
public enum WorkType {
    ALBUM, SINGLE, SONG, COMPOSITION, OTHER
}
