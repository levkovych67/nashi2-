package com.nashi.wiki;

import com.nashi.geo.Region;

/** A place in Ukraine with coordinates and its region. */
public record WikiPlace(String qid, String labelUk, double lat, double lon, Region region) {
}
