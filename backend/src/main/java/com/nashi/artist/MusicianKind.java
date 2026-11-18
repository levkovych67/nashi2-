package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** A solo musician or a group. */
@Schema(enumAsRef = true)
public enum MusicianKind {
    PERSON, BAND
}
