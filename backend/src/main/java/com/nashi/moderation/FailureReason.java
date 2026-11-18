package com.nashi.moderation;

import java.util.regex.Pattern;

/**
 * The {@code note} of a {@code FAILED} moderation entry: the exception's class and a short message. Anything shaped
 * like a bot token is masked, so a client error that quotes the request URL cannot put the secret into the database.
 */
final class FailureReason {

    private static final int MAX_LENGTH = 300;
    private static final Pattern BOT_TOKEN = Pattern.compile("\\d{5,}:[A-Za-z0-9_-]{30,}");

    private FailureReason() {
    }

    static String of(Throwable e) {
        String message = e.getMessage();
        String text = e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
        text = BOT_TOKEN.matcher(text).replaceAll("***");
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 1) + "…";
    }
}
