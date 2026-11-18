package com.nashi.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.ReplyParameters;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@link TelegramGateway} over the Bot API. With sending disabled it never touches the client. */
@Component
public class BotApiTelegramGateway implements TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(BotApiTelegramGateway.class);
    private static final String HTML = "HTML";
    /** Bot API limit for one media group. */
    private static final int MAX_ALBUM = 10;

    private final TelegramClient client;
    private final TelegramProperties props;
    private final TelegramRetry retry;

    public BotApiTelegramGateway(TelegramClient client, TelegramProperties props, TelegramRetry retry) {
        this.client = client;
        this.props = props;
        this.retry = retry;
    }

    @Override
    public List<Integer> sendAlbum(List<NamedBytes> photos) {
        if (photos.isEmpty()) {
            return List.of();
        }
        if (photos.size() > MAX_ALBUM) {
            throw new IllegalArgumentException("An album holds at most " + MAX_ALBUM + " photos");
        }
        if (disabled("send " + photos.size() + " photo(s)")) {
            return List.of();
        }
        String chatId = chatId();
        // A request is built per attempt: an uploaded stream is spent after the first try.
        if (photos.size() == 1) {
            NamedBytes photo = photos.get(0);
            Message sent = call("sendPhoto", () -> client.execute(SendPhoto.builder()
                    .chatId(chatId)
                    .messageThreadId(props.moderation().threadId())
                    .photo(new InputFile(new ByteArrayInputStream(photo.bytes()), photo.filename()))
                    .build()));
            return List.of(sent.getMessageId());
        }
        List<Message> sent = call("sendMediaGroup", () -> {
            List<InputMedia> medias = new ArrayList<>();
            for (NamedBytes photo : photos) {
                medias.add(new InputMediaPhoto(new ByteArrayInputStream(photo.bytes()), photo.filename()));
            }
            return client.execute(SendMediaGroup.builder()
                    .chatId(chatId)
                    .messageThreadId(props.moderation().threadId())
                    .medias(medias)
                    .build());
        });
        return sent.stream().map(Message::getMessageId).toList();
    }

    @Override
    public int sendControl(String html, List<List<InlineButton>> keyboard, Integer replyTo) {
        if (disabled("send a control message")) {
            return -1;
        }
        SendMessage request = message(html);
        if (replyTo != null) {
            request.setReplyParameters(new ReplyParameters(replyTo));
        }
        if (keyboard != null && !keyboard.isEmpty()) {
            request.setReplyMarkup(markup(keyboard));
        }
        return call("sendMessage", () -> client.execute(request)).getMessageId();
    }

    @Override
    public void editControl(int messageId, String html) {
        if (disabled("edit message " + messageId)) {
            return;
        }
        EditMessageText request = EditMessageText.builder()
                .chatId(chatId())
                .messageId(messageId)
                .text(html)
                .parseMode(HTML)
                .build();
        call("editMessageText", () -> client.execute(request));
    }

    @Override
    public void answerCallback(String callbackQueryId, String text) {
        if (disabled("answer a callback")) {
            return;
        }
        AnswerCallbackQuery request = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .build();
        call("answerCallbackQuery", () -> client.execute(request));
    }

    @Override
    public void sendText(String html) {
        if (disabled("send a text message")) {
            return;
        }
        SendMessage request = message(html);
        call("sendMessage", () -> client.execute(request));
    }

    @Override
    public Set<Long> chatAdminIds() {
        if (disabled("list chat administrators")) {
            return Set.of();
        }
        GetChatAdministrators request = new GetChatAdministrators(chatId());
        Set<Long> ids = new HashSet<>();
        call("getChatAdministrators", () -> client.execute(request)).forEach(m -> ids.add(m.getUser().getId()));
        return ids;
    }

    private SendMessage message(String html) {
        return SendMessage.builder()
                .chatId(chatId())
                .messageThreadId(props.moderation().threadId())
                .text(html)
                .parseMode(HTML)
                .build();
    }

    private static InlineKeyboardMarkup markup(List<List<InlineButton>> keyboard) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (List<InlineButton> row : keyboard) {
            InlineKeyboardRow out = new InlineKeyboardRow();
            for (InlineButton b : row) {
                out.add(InlineKeyboardButton.builder().text(b.text()).callbackData(b.callbackData()).build());
            }
            rows.add(out);
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private String chatId() {
        return Long.toString(props.moderation().chatId());
    }

    private boolean disabled(String what) {
        if (props.sendingEnabled()) {
            return false;
        }
        log.info("Telegram sending off, not doing: {}", what);
        return true;
    }

    private <T> T call(String method, TelegramRetry.Call<T> call) {
        try {
            return retry.execute(call);
        } catch (TelegramApiException e) {
            throw new TelegramGatewayException("Telegram " + method + " failed: " + e.getMessage(), e);
        }
    }
}
