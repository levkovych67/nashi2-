package com.nashi.telegram;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BotApiTelegramGatewayTest {

    private final TelegramClient client = mock(TelegramClient.class);
    private final TelegramRetry retry = new TelegramRetry(millis -> { });

    private BotApiTelegramGateway gateway(boolean sending, Integer threadId) {
        TelegramProperties props = new TelegramProperties("secret-token", false, sending,
                new TelegramProperties.Moderation(-1001L, threadId, ApproverPolicy.CHAT_ADMINS));
        return new BotApiTelegramGateway(client, props, retry);
    }

    private static Message sent(int id) {
        Message m = new Message();
        m.setMessageId(id);
        return m;
    }

    private static NamedBytes photo(String name) {
        return new NamedBytes(name, new byte[] {1, 2, 3});
    }

    @Test
    void gatewayIsNoOpWhenSendingDisabled() {
        BotApiTelegramGateway gateway = gateway(false, null);

        assertThat(gateway.sendAlbum(List.of(photo("a.jpg")))).isEmpty();
        assertThat(gateway.sendControl("x", List.of(), null)).isEqualTo(-1);
        gateway.editControl(1, "x");
        gateway.answerCallback("q", "ok");
        gateway.sendText("x");
        assertThat(gateway.chatAdminIds()).isEmpty();

        verifyNoInteractions(client);
    }

    @Test
    void gatewayAddsThreadIdWhenConfigured() throws TelegramApiException {
        when(client.execute(any(SendMessage.class))).thenReturn(sent(10));

        gateway(true, 42).sendText("hi");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getMessageThreadId()).isEqualTo(42);
        assertThat(captor.getValue().getChatId()).isEqualTo("-1001");
        assertThat(captor.getValue().getParseMode()).isEqualTo("HTML");
    }

    @Test
    void controlMessageCarriesKeyboardAndReply() throws TelegramApiException {
        when(client.execute(any(SendMessage.class))).thenReturn(sent(11));

        int id = gateway(true, null).sendControl("ok?",
                List.of(List.of(new InlineButton("Так", "sub:a:y"), new InlineButton("Ні", "sub:a:n"))), 5);

        assertThat(id).isEqualTo(11);
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(captor.capture());
        SendMessage request = captor.getValue();
        assertThat(request.getReplyParameters().getMessageId()).isEqualTo(5);
        assertThat(request.getMessageThreadId()).isNull();
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) request.getReplyMarkup();
        assertThat(markup.getKeyboard()).hasSize(1);
        assertThat(markup.getKeyboard().get(0)).extracting("callbackData").containsExactly("sub:a:y", "sub:a:n");
    }

    @Test
    void editControlDropsKeyboard() throws TelegramApiException {
        gateway(true, 42).editControl(7, "done");

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo(7);
        assertThat(captor.getValue().getReplyMarkup()).isNull();
    }

    @Test
    void singlePhotoIsUploadedAsSendPhoto() throws TelegramApiException {
        when(client.execute(any(SendPhoto.class))).thenReturn(sent(20));

        List<Integer> ids = gateway(true, 42).sendAlbum(List.of(photo("a.jpg")));

        assertThat(ids).containsExactly(20);
        ArgumentCaptor<SendPhoto> captor = ArgumentCaptor.forClass(SendPhoto.class);
        verify(client).execute(captor.capture());
        InputFile file = captor.getValue().getPhoto();
        assertThat(file.isNew()).isTrue();
        assertThat(file.getMediaName()).isEqualTo("a.jpg");
        assertThat(captor.getValue().getMessageThreadId()).isEqualTo(42);
    }

    @Test
    void severalPhotosAreUploadedAsMediaGroupInOrder() throws TelegramApiException {
        when(client.execute(any(SendMediaGroup.class))).thenReturn(List.of(sent(30), sent(31)));

        List<Integer> ids = gateway(true, null).sendAlbum(List.of(photo("a.jpg"), photo("b.jpg")));

        assertThat(ids).containsExactly(30, 31);
        ArgumentCaptor<SendMediaGroup> captor = ArgumentCaptor.forClass(SendMediaGroup.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getMedias()).hasSize(2);
        assertThat(captor.getValue().getMedias()).extracting("mediaName").containsExactly("a.jpg", "b.jpg");
    }

    @Test
    void failuresSurfaceAsGatewayException() throws TelegramApiException {
        when(client.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("bad"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gateway(true, null).sendText("x"))
                .isInstanceOf(TelegramGatewayException.class);
    }
}
