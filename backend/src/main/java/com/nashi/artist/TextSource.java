package com.nashi.artist;

/** Origin and license of {@code description}; absent for text written by the applicant. */
public record TextSource(String type, String title, String url, String license, String licenseUrl) {
}
