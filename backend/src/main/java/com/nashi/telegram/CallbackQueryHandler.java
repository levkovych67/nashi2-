package com.nashi.telegram;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/** Handles a button press from the moderation chat. The router calls it only for the configured chat. */
public interface CallbackQueryHandler {

    void handle(CallbackQuery query);
}
