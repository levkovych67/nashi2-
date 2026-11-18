package com.nashi.telegram;

import java.util.List;
import java.util.Set;

/**
 * Everything the app says to the moderation chat. All methods target {@code moderation.chatId} (and its thread
 * when configured) with HTML parse mode. When sending is disabled they log and return empty results.
 */
public interface TelegramGateway {

    /** Uploads the photos (1 as a photo, 2 or more as an album) and returns the message ids in order. */
    List<Integer> sendAlbum(List<NamedBytes> photos);

    /** Sends a message with an inline keyboard; returns its id, or -1 when sending is disabled. */
    int sendControl(String html, List<List<InlineButton>> keyboard, Integer replyTo);

    /** Replaces the text and removes the buttons. */
    void editControl(int messageId, String html);

    void answerCallback(String callbackQueryId, String text);

    void sendText(String html);

    /** User ids of the chat's administrators. */
    Set<Long> chatAdminIds();
}
