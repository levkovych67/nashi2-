package com.nashi.config;

/** {@code GET /config/public} ({@code docs/02-contracts.md} 2.11); {@code turnstileSiteKey} is {@code null} while off. */
public record PublicConfig(int fragmentSeconds, MapConfig map, LikesConfig likes, SubmissionConfig submission,
                           String turnstileSiteKey) {

    public record MapConfig(int regionMaxZoom, int placeMaxZoom, int minZoom, int maxZoom) {
    }

    public record LikesConfig(int batchMax, int flushMs) {
    }

    public record SubmissionConfig(int maxImages, int maxImageMb, int summaryMin, int summaryMax) {
    }
}
