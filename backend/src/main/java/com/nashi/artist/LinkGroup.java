package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Group of a link platform ({@code docs/02-contracts.md} section 2, enumerations). */
@Schema(enumAsRef = true)
public enum LinkGroup {
    MUSIC, VIDEO, REFERENCE, SOCIAL
}
