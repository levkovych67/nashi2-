package com.nashi.enums;

public enum ArtistStatus {
    /**
     * Default state when a user submits a suggestion.
     * Not visible in public API.
     * Waiting for Admin review.
     */
    PENDING,

    /**
     * Reviewed and published by Admin.
     * Visible in public API.
     */
    APPROVED,

    /**
     * Denied by Admin.
     * Not visible.
     * Triggers the S3 Cleanup Job (deletion of images/audio) after 24h.
     */
    REJECTED,

    /**
     * Optional: If an entry needs to be hidden temporarily without deleting files.
     */
    HIDDEN
}