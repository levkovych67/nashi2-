package com.nashi.submission;

/** Published after a {@code PENDING} submission is saved; moderation sends it to Telegram. */
public record SubmissionCreatedEvent(String submissionId) {
}
