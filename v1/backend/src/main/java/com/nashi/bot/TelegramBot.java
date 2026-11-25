package com.nashi.bot;


import com.nashi.api.exception.AlreadyProcessedException;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.service.ArtistApprovalService;
import com.nashi.config.TelegramProperties;
import com.nashi.service.EventApprovalService;
import com.nashi.service.impl.artist.ArtistVisibilityService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import com.nashi.wiki.WikiImportRequested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.nashi.util.StringUtils.generateRandomString;

@Slf4j
@Component
public class TelegramBot extends AbstractBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final ArtistApprovalService artistApprovalService;
    private final EventApprovalService eventApprovalService;
    private final ArtistVisibilityService artistVisibilityService;
    private final TelegramProperties telegramProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TelegramBot(ArtistApprovalService artistApprovalService, EventApprovalService eventApprovalService,
                       ArtistVisibilityService artistVisibilityService,
                       TelegramProperties telegramProperties, ApplicationEventPublisher eventPublisher) {
        this(new OkHttpTelegramClient(httpClient(), telegramProperties.botToken()),
                artistApprovalService, eventApprovalService, artistVisibilityService, telegramProperties, eventPublisher);
    }

    TelegramBot(TelegramClient telegramClient, ArtistApprovalService artistApprovalService,
                EventApprovalService eventApprovalService, ArtistVisibilityService artistVisibilityService,
                TelegramProperties telegramProperties, ApplicationEventPublisher eventPublisher) {
        this.telegramClient = telegramClient;
        this.artistApprovalService = artistApprovalService;
        this.eventApprovalService = eventApprovalService;
        this.artistVisibilityService = artistVisibilityService;
        this.telegramProperties = telegramProperties;
        this.eventPublisher = eventPublisher;
    }

    private static OkHttpClient httpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getBotToken() {
        return telegramProperties.botToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }


    /**
     * Only the admin chat is listened to. Callbacks (the approve/reject buttons) and texts from any other chat are
     * logged and ignored, with no reply.
     */
    @Override
    public void consume(Update update) {
        String adminChatId = telegramProperties.adminChatId();
        if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            String chatId = callback.getMessage() != null ? String.valueOf(callback.getMessage().getChatId()) : null;
            if (!isAdminChat(chatId, adminChatId)) {
                log.warn("Ignored callback from chat {}: not the admin chat", chatId);
                return;
            }
            handleCallback(callback);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            String chatId = String.valueOf(update.getMessage().getChatId());
            if (!isAdminChat(chatId, adminChatId)) {
                log.warn("Ignored message from chat {}: not the admin chat", chatId);
                return;
            }
            handleAdminText(update.getMessage().getText());
        }
    }

    private void handleCallback(CallbackQuery callback) {
        String callbackData = callback.getData();
        log.info("Received callback: {}", callbackData);

        String[] parts = callbackData == null ? new String[0] : callbackData.split(":");

        if (parts.length < 3) {
            log.warn("Invalid callback data format: {}", callbackData);
            return;
        }

        String entityType = parts[0]; // "ARTIST" or "EVENT"
        String action = parts[1];     // "APPROVE" or "REJECT"
        String entityId = parts[2];   // The UUID or ID

        try {
            switch (entityType) {
                case "ARTIST" -> handleArtistCallback(action, entityId);
                case "EVENT" -> handleEventCallback(action, entityId);
                default -> log.warn("Unknown entity type: {}", entityType);
            }
        } catch (AlreadyProcessedException e) {
            // An old button: nothing changed, tell the admin why
            sendAdminText(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing {} callback for ID: {}", entityType, entityId, e);
            // Optional: Send error message to admin chat
        }

        // Good practice: Answer the callback to stop the loading animation in Telegram
        answerCallbackQuery(callback.getId());
    }

    /** A text from the admin chat: /wiki_import, /hide, /show, or anything else for the "alive" check. */
    private void handleAdminText(String text) {
        if (isWikiImportText(text)) {
            log.info("Wikipedia import requested from the admin chat");
            eventPublisher.publishEvent(new WikiImportRequested());
            return;
        }
        Optional<AdminCommand> command = AdminCommand.parse(text);
        if (command.isEmpty()) {
            sendAdminText("alive");
            return;
        }
        try {
            sendAdminText(run(command.get()));
        } catch (Exception e) {
            log.error("Admin command {} failed", command.get().action(), e);
        }
    }

    private String run(AdminCommand command) {
        if (command.argument().isEmpty()) {
            return command.usage();
        }
        return switch (command.action()) {
            case HIDE -> artistVisibilityService.hide(command.argument());
            case SHOW -> artistVisibilityService.show(command.argument());
        };
    }

    /** True when {@code chatId} is the configured admin chat ({@code telegram.admin-chat-id}). */
    public static boolean isAdminChat(String chatId, String adminChatId) {
        return adminChatId != null && chatId != null && adminChatId.trim().equals(chatId);
    }

    /** True for "/wiki_import" or "/wiki_import@bot_name". */
    public static boolean isWikiImportText(String text) {
        return text != null && text.trim().matches("/wiki_import(@\\w+)?");
    }

    /** Sends plain text to the admin chat. */
    public void sendAdminText(String text) {
        executeWithRetry(SendMessage.builder()
                .text(text)
                .chatId(telegramProperties.adminChatId())
                .build());
    }


    private void handleArtistCallback(String action, String artistId) {
        if ("APPROVE".equals(action)) {
            TelegramNewsPostDTO newsPost = artistApprovalService.approveArtist(artistId);
            sendNewsToChannel(newsPost);
            log.info("Successfully approved artist: {}", artistId);
        } else if ("REJECT".equals(action)) {
            artistApprovalService.rejectArtist(artistId);
            log.info("Successfully rejected artist: {}", artistId);
        }
    }

    private void handleEventCallback(String action, String eventId) {
        if ("APPROVE".equals(action)) {
            // Assuming eventService.approveEvent returns the post DTO similarly to artists
            TelegramNewsPostDTO newsPost = eventApprovalService.approveEvent(eventId);
            sendNewsToChannel(newsPost);
            log.info("Successfully approved event: {}", eventId);
        } else if ("REJECT".equals(action)) {
            eventApprovalService.rejectEvent(eventId);
            log.info("Successfully rejected event: {}", eventId);
        }
    }

    private void answerCallbackQuery(String callbackQueryId) {
        // Basic implementation to stop the "loading" circle on the button
        // You might need to inject TelegramClient or use execute() directly
        // execute(new AnswerCallbackQuery(callbackQueryId));
    }

    public void executeWithRetry(SendMediaGroup mediaGroup) {
        if (sendingOff("media group")) return;
        executeWithRetry(mediaGroup, telegramClient);
    }

    public void executeWithRetry(SendMessage sendMessage) {
        if (sendingOff("message")) return;
        executeWithRetry(sendMessage, telegramClient);
    }

    public void executeWithRetry(SendPhoto photo) {
        if (sendingOff("photo")) return;
        executeWithRetry(photo, telegramClient);
    }

    public void executeWithRetry(SendAudio audio) {
        if (sendingOff("audio")) return;
        executeWithRetry(audio, telegramClient);
    }

    /** With telegram.enabled=false every outgoing call ends here as a logged no-op. */
    private boolean sendingOff(String what) {
        if (telegramProperties.isEnabled()) return false;
        log.info("Telegram is off (telegram.enabled=false): {} not sent", what);
        return true;
    }

    public void sendNewsToChannel(TelegramNewsPostDTO post) {
        // Checked first: the audio below is downloaded before it is sent.
        if (sendingOff("channel post")) return;
        if (post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
            SendPhoto sendPhoto = SendPhoto.builder()
                    .chatId(telegramProperties.channelId())
                    .photo(new InputFile(post.getImageUrl()))
                    .caption(post.getCaption())
                    .parseMode("Markdown")
                    .build();
            executeWithRetry(sendPhoto);
        } else {

            SendMessage sendMessage = SendMessage.builder()
                    .chatId(telegramProperties.channelId())
                    .text(post.getCaption())
                    .parseMode("Markdown")
                    .build();
            executeWithRetry(sendMessage);
        }

        if (post.getAudioUrl() != null && !post.getAudioUrl().isBlank()) {

            try {
                SendAudio sendAudio =
                        SendAudio.builder()
                                .chatId(telegramProperties.channelId())
                                .audio(new InputFile(new URL(post.getAudioUrl()).openStream(),
                                        generateRandomString(5)))
                                .caption("🎧 Слухати").build();


                executeWithRetry(sendAudio);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        log.info("News posted to channel " + telegramProperties.channelId() + " successfully.");

    }
}
