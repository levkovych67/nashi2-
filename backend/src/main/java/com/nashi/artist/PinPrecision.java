package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** How exactly the pin is placed ({@code docs/02-contracts.md} section 4). */
@Schema(enumAsRef = true)
public enum PinPrecision {
    EXACT, SETTLEMENT, CITY
}
