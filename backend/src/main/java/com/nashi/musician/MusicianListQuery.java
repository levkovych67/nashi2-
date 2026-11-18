package com.nashi.musician;

import com.nashi.geo.Region;

import java.util.List;

/** A validated list request: {@code regions} may be empty, {@code q} is {@code null} when not given. */
public record MusicianListQuery(List<Region> regions, String q, MusicianSort sort, int page, int size) {
}
