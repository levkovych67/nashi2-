package com.nashi.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Sends updates from the moderation chat to the handlers and drops everything else. Handler failures are
 * logged so polling never stops.
 */
@Component
public class TelegramUpdateRouter implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateRouter.class);

    private final TelegramProperties props;
    private final ObjectProvider<CallbackQueryHandler> callbackHandler;
    private final ObjectProvider<CommandHandler> commandHandler;

    public TelegramUpdateRouter(TelegramProperties props,
                                ObjectProvider<CallbackQueryHandler> callbackHandler,
                                ObjectProvider<CommandHandler> commandHandler) {
        this.props = props;
        this.callbackHandler = callbackHandler;
        this.commandHandler = commandHandler;
    }

    @Override
    public void consume(Update update) {
        try {
            route(update);
        } catch (RuntimeException e) {
            log.error("Telegram update {} failed", update.getUpdateId(), e);
        }
    }

    private void route(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery query = update.getCallbackQuery();
            if (query.getMessage() == null) {
                log.warn("Ignoring callback query without a message");
                return;
            }
            if (!fromModerationChat(query.getMessage().getChatId())) {
                return;
            }
            CallbackQueryHandler handler = callbackHandler.getIfAvailable();
            if (handler == null) {
                log.debug("No callback handler yet, ignoring callback query");
                return;
            }
            handler.handle(query);
        } else if (update.hasMessage()) {
            Message message = update.getMessage();
            if (!fromModerationChat(message.getChatId())) {
                return;
            }
            if (message.getText() == null || !message.getText().startsWith("/")) {
                return;
            }
            CommandHandler handler = commandHandler.getIfAvailable();
            if (handler == null) {
                log.debug("No command handler yet, ignoring command");
                return;
            }
            handler.handle(message);
        }
    }

    private boolean fromModerationChat(Long chatId) {
        if (chatId != null && chatId == props.moderation().chatId()) {
            return true;
        }
        log.warn("Ignoring update from foreign chat {}", chatId);
        return false;
    }
}
