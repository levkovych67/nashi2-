package com.nashi.artist;

/** Partial dates {@code YYYY}, {@code YYYY-MM} or {@code YYYY-MM-DD}; any may be {@code null}. */
public record LifeDates(String birth, String death, String activeFrom, String activeTo) {
}
