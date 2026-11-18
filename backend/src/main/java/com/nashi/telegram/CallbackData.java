package com.nashi.telegram;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payload of a moderation button: {@code sub:<24 hex>:y|n}, 31 bytes, well under Telegram's 64-byte limit.
 * Anything else (including the V1 format) parses to empty.
 */
public record CallbackData(String submissionId, Verdict verdict) {

    private static final Pattern FORMAT = Pattern.compile("sub:([0-9a-f]{24}):([yn])");

    public String encode() {
        return "sub:" + submissionId + ":" + (verdict == Verdict.YES ? "y" : "n");
    }

    public static Optional<CallbackData> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(raw);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new CallbackData(m.group(1), "y".equals(m.group(2)) ? Verdict.YES : Verdict.NO));
    }
}
