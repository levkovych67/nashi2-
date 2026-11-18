package com.nashi.submission;

/** Where a submission is in moderation; {@code DISCARDED} = the bot trap fired ({@code docs/02-contracts.md} section 3). */
public enum SubmissionStatus {
    PENDING, APPROVED, REJECTED, DISCARDED
}
