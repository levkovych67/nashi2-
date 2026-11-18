package com.nashi.wiki;

/** Sends a short plain text to the moderation group (the import's report). */
@FunctionalInterface
public interface AdminNotifier {
    void send(String text);
}
