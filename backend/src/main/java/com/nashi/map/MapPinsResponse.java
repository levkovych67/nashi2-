package com.nashi.map;

import java.util.List;

/** {@code version} is the ETag without quotes. */
public record MapPinsResponse(String version, List<MapPin> pins) {
}
