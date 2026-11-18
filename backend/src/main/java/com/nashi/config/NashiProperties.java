package com.nashi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Application settings under {@code nashi.*}. Defaults are the values of {@code docs/02-contracts.md} 2.11
 * ({@code GET /api/v2/config/public}). Nested types end in {@code Props} so they never shadow {@code java.util.Map}
 * or domain types such as {@code Submission}. Storage ({@code nashi.s3.*}) and Telegram ({@code nashi.telegram.*})
 * have their own properties classes.
 *
 * @param siteBaseUrl public site URL without a trailing slash, for links in Telegram messages
 *                    ({@code nashi.site-base-url})
 */
@ConfigurationProperties("nashi")
public record NashiProperties(
        @DefaultValue CorsProps cors,
        @DefaultValue AudioProps audio,
        @DefaultValue MapProps map,
        @DefaultValue LikesProps likes,
        @DefaultValue SubmissionProps submission,
        @DefaultValue SecurityProps security,
        String siteBaseUrl) {

    /** @param allowedOrigins origins or origin patterns ({@code https://*.example}) allowed to call the API */
    public record CorsProps(@DefaultValue List<String> allowedOrigins) {
    }

    /** @param fragmentSeconds length of the random audio fragment */
    public record AudioProps(@DefaultValue("15") int fragmentSeconds) {
    }

    /** Zoom thresholds: regions up to {@code regionMaxZoom}, places up to {@code placeMaxZoom}, then pins. */
    public record MapProps(
            @DefaultValue("7") int regionMaxZoom,
            @DefaultValue("10") int placeMaxZoom,
            @DefaultValue("5") int minZoom,
            @DefaultValue("17") int maxZoom) {
    }

    /** @param batchMax most likes in one request; @param flushMs how long the client collects likes before sending */
    public record LikesProps(@DefaultValue("50") int batchMax, @DefaultValue("600") int flushMs) {
    }

    public record SubmissionProps(
            @DefaultValue("5") int maxImages,
            @DefaultValue("10") int maxImageMb,
            @DefaultValue("30") int summaryMin,
            @DefaultValue("1000") int summaryMax) {
    }

    /** @param ipHashSecret HMAC key for hashing client IPs; production gets it from the secrets file */
    public record SecurityProps(String ipHashSecret) {
    }
}
