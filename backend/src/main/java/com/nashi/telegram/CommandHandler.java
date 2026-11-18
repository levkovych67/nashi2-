package com.nashi.telegram;

import org.telegram.telegrambots.meta.api.objects.message.Message;

/** Handles a message starting with {@code /} from the moderation chat. */
public interface CommandHandler {

    void handle(Message message);
}
