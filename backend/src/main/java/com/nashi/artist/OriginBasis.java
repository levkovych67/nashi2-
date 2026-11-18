package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why the origin is where it is: {@code BIRTH}, {@code FORMATION} (where a band was founded) or {@code ORIGIN} (the applicant's words). */
@Schema(enumAsRef = true)
public enum OriginBasis {
    BIRTH, FORMATION, ORIGIN
}
