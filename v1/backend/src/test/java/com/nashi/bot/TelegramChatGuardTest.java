package com.nashi.bot;

import com.nashi.api.exception.AlreadyProcessedException;
import com.nashi.config.TelegramProperties;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.EventStatus;
import com.nashi.service.ArtistApprovalService;
import com.nashi.service.EventApprovalService;
import com.nashi.service.impl.artist.ArtistVisibilityService;
import com.nashi.wiki.WikiImportRequested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelegramChatGuardTest {

    private static final long ADMIN = -100500L;
    private static final long STRANGER = 777L;

    private final TelegramClient client = mock(TelegramClient.class);
    private final ArtistApprovalService artistApproval = mock(ArtistApprovalService.class);
    private final EventApprovalService eventApproval = mock(EventApprovalService.class);
    private final ArtistVisibilityService visibility = mock(ArtistVisibilityService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TelegramBot bot = new TelegramBot(client, artistApproval, eventApproval, visibility,
            new TelegramProperties("test-token", String.valueOf(ADMIN), "-200", true), events);

    @Test
    void callbackFromOtherChatIgnored() {
        bot.consume(callback(STRANGER, "ARTIST:APPROVE:a1"));
        bot.consume(callback(STRANGER, "ARTIST:REJECT:a1"));
        bot.consume(callback(STRANGER, "EVENT:APPROVE:e1"));
        bot.consume(callback(null, "ARTIST:APPROVE:a1")); // inline message: no chat at all

        verifyNoInteractions(artistApproval, eventApproval, client);
    }

    @Test
    void callbackFromAdminChatHandled() throws Exception {
        when(artistApproval.approveArtist("a1")).thenReturn(TelegramNewsPostDTO.builder().caption("Новина").build());

        bot.consume(callback(ADMIN, "ARTIST:APPROVE:a1"));
        bot.consume(callback(ADMIN, "EVENT:REJECT:e1"));

        verify(artistApproval).approveArtist("a1");
        verify(eventApproval).rejectEvent("e1");
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertEquals("-200", sent.getValue().getChatId());
    }

    @Test
    void alreadyProcessedButtonAnswersTheAdmin() throws Exception {
        when(artistApproval.approveArtist("a1")).thenThrow(new AlreadyProcessedException(ArtistStatus.HIDDEN));
        doThrow(new AlreadyProcessedException(EventStatus.APPROVED)).when(eventApproval).rejectEvent("e1");

        bot.consume(callback(ADMIN, "ARTIST:APPROVE:a1"));
        bot.consume(callback(ADMIN, "EVENT:REJECT:e1"));

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client, times(2)).execute(sent.capture());
        // Both answers go to the admin chat; nothing is posted to the channel.
        assertEquals(String.valueOf(ADMIN), sent.getAllValues().get(0).getChatId());
        assertEquals("Заявку вже оброблено (статус: HIDDEN)", sent.getAllValues().get(0).getText());
        assertEquals(String.valueOf(ADMIN), sent.getAllValues().get(1).getChatId());
        assertEquals("Заявку вже оброблено (статус: APPROVED)", sent.getAllValues().get(1).getText());
    }

    @Test
    void wikiImportFromAdminChatStartsTheImport() {
        bot.consume(text(ADMIN, "/wiki_import"));
        bot.consume(text(ADMIN, "/wiki_import@nashi_bot"));

        verify(events, times(2)).publishEvent(isA(WikiImportRequested.class));
        verifyNoInteractions(client);
    }

    @Test
    void textFromOtherChatGetsNoReply() {
        bot.consume(text(STRANGER, "привіт"));
        bot.consume(text(STRANGER, "/hide okean-elzy"));
        bot.consume(text(STRANGER, "/show okean-elzy"));
        bot.consume(text(STRANGER, "/wiki_import"));

        verifyNoInteractions(client, visibility, events);
    }

    @Test
    void hideAndShowFromAdminChatReplyWithTheResult() throws Exception {
        when(visibility.hide("okean-elzy")).thenReturn("Приховано: Океан Ельзи (okean-elzy). Повторно не імпортується.");

        bot.consume(text(ADMIN, "/hide okean-elzy"));

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertEquals(String.valueOf(ADMIN), sent.getValue().getChatId());
        assertEquals("Приховано: Океан Ельзи (okean-elzy). Повторно не імпортується.", sent.getValue().getText());
    }

    @Test
    void commandWithoutArgumentExplainsUsage() throws Exception {
        bot.consume(text(ADMIN, "/show"));

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertEquals("Використання: /show <slug або Q-id>", sent.getValue().getText());
        verifyNoInteractions(visibility);
    }

    @Test
    void adminChatStillGetsAlive() throws Exception {
        bot.consume(text(ADMIN, "привіт"));

        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertEquals("alive", sent.getValue().getText());
    }

    private static Update callback(Long chatId, String data) {
        CallbackQuery query = new CallbackQuery();
        query.setId("cb1");
        query.setData(data);
        if (chatId != null) {
            query.setMessage(message(chatId, null));
        }
        Update update = new Update();
        update.setCallbackQuery(query);
        return update;
    }

    private static Update text(long chatId, String text) {
        Update update = new Update();
        update.setMessage(message(chatId, text));
        return update;
    }

    private static Message message(long chatId, String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "supergroup"));
        message.setText(text);
        return message;
    }
}
