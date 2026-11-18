package com.nashi.submission;

/** The Telegram user who acted on a submission. */
public record Moderator(long telegramUserId, String username, String displayName) {
}
