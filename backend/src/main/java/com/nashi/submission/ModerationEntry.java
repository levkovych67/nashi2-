package com.nashi.submission;

import java.time.Instant;

/** One line of {@code moderationLog}; {@code by} is {@code null} for system actions. */
public record ModerationEntry(ModerationAction action, Moderator by, Instant at, String note) {
}
