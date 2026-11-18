package com.nashi.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class TelegramUpdateRouterTest {

    private static final long CHAT = -1001L;

    private final CallbackQueryHandler callbacks = mock(CallbackQueryHandler.class);
    private final CommandHandler commands = mock(CommandHandler.class);
    private final ObjectProvider<CallbackQueryHandler> callbackProvider = mock(ObjectProvider.class);
    private final ObjectProvider<CommandHandler> commandProvider = mock(ObjectProvider.class);
    private final TelegramUpdateRouter router;

    TelegramUpdateRouterTest() {
        when(callbackProvider.getIfAvailable()).thenReturn(callbacks);
        when(commandProvider.getIfAvailable()).thenReturn(commands);
        TelegramProperties props = new TelegramProperties("t", false, false,
                new TelegramProperties.Moderation(CHAT, null, ApproverPolicy.CHAT_ADMINS));
        router = new TelegramUpdateRouter(props, callbackProvider, commandProvider);
    }

    private static Update message(long chatId, String text) {
        Message message = new Message();
        message.setChat(Chat.builder().id(chatId).type("supergroup").build());
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private static Update callback(long chatId) {
        Message message = new Message();
        message.setChat(Chat.builder().id(chatId).type("supergroup").build());
        CallbackQuery query = new CallbackQuery();
        query.setId("q1");
        query.setMessage(message);
        Update update = new Update();
        update.setCallbackQuery(query);
        return update;
    }

    @Test
    void routerIgnoresOtherChats() {
        router.consume(message(5L, "/ping"));
        router.consume(callback(5L));
        verifyNoInteractions(callbacks, commands);
    }

    @Test
    void routerDispatchesCallbacksAndCommands() {
        Update cb = callback(CHAT);
        router.consume(cb);
        verify(callbacks).handle(cb.getCallbackQuery());

        Update cmd = message(CHAT, "/ping@NashiBot");
        router.consume(cmd);
        verify(commands).handle(cmd.getMessage());
    }

    @Test
    void routerIgnoresPlainMessages() {
        router.consume(message(CHAT, "hello"));
        verifyNoInteractions(commands, callbacks);
    }

    @Test
    void routerIgnoresUpdatesWhenNoHandlerExists() {
        when(callbackProvider.getIfAvailable()).thenReturn(null);
        when(commandProvider.getIfAvailable()).thenReturn(null);
        router.consume(callback(CHAT));
        router.consume(message(CHAT, "/ping"));
        verify(callbacks, never()).handle(any());
    }

    @Test
    void routerSurvivesHandlerFailures() {
        doThrow(new IllegalStateException("boom")).when(commands).handle(any());
        router.consume(message(CHAT, "/hide"));
        Update cb = callback(CHAT);
        router.consume(cb);
        verify(callbacks).handle(cb.getCallbackQuery());
    }
}
