package com.nashi.wiki;

/** Sends a short text to the admin chat. */
@FunctionalInterface
public interface AdminNotifier {
    void send(String text);
}
