package com.nashi.map;

import com.nashi.geo.Region;

/** {@code center} = [lon, lat], {@code bbox} = [west, south, east, north]. */
public record RegionStat(Region code, String name, double[] center, double[] bbox, long count) {
}
