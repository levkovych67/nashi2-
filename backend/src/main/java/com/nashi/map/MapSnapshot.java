package com.nashi.map;

import java.util.List;

/** Everything the map endpoints serve, built together so that they agree with one {@code version}. */
public record MapSnapshot(String version, List<MapPin> pins, List<RegionStat> regions, List<PlaceStat> places) {
}
