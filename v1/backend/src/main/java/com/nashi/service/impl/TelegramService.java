package com.nashi.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.bot.TelegramBot;
import com.nashi.domain.Artist;
import com.nashi.domain.Event;
import com.nashi.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramBot telegramBot;
    private final S3Service s3Service;
    private final com.nashi.config.TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendArtistForApproval(Artist artist) {
        // Checked here as well as in TelegramBot: the files below are fetched from S3 before they are sent.
        if (!telegramProperties.isEnabled()) {
            log.info("Telegram is off (telegram.enabled=false): artist {} not sent for approval", artist.getId());
            return;
        }

        sendImages(artist);
        sendInfoWithButtons(artist);

        try {
            sendAudioFiles(artist);
        } catch (Exception e) {
            log.error("Error sending artist to Telegram", e);
        }

    }

    private void sendAudioFiles(Artist artist) throws IOException {
        if (artist.getDemoTrackKeys() == null) return;

        for (String trackKey : artist.getDemoTrackKeys()) {
            String trackUrl = s3Service.getFileUrl(trackKey);
            try (var in = new URL(trackUrl).openStream()) {
                SendAudio sendAudio = SendAudio.builder()
                        .audio(new InputFile(in, trackKey))
                        .chatId(telegramProperties.adminChatId())
                        .build();
                telegramBot.executeWithRetry(sendAudio);
            }
        }
    }

    private void sendImages(Artist artist) {
        if (artist.getImageKeys() == null || artist.getImageKeys().isEmpty()) return;

        List<String> imageUrls = artist.getImageKeys().stream()
                .map(e -> e.contains("artists/") ? s3Service.getFileUrl(e) : e)
                .toList();

        if (imageUrls.size() == 1) {
            SendPhoto sendPhoto = SendPhoto
                    .builder()
                    .chatId(telegramProperties.adminChatId())
                    .photo(new InputFile(imageUrls.get(0)))
                    .build();
            telegramBot.executeWithRetry(sendPhoto);

        } else {
            // Media Group (Album)
            List<InputMedia> mediaGroup = new ArrayList<>();
            for (String url : imageUrls) {
                InputMediaPhoto photo = InputMediaPhoto
                        .builder()
                        .media(url)
                        .build();
                mediaGroup.add(photo);
            }

            SendMediaGroup sendMediaGroup = SendMediaGroup.builder()
                    .medias(mediaGroup)
                    .chatId(telegramProperties.adminChatId())
                    .build();
            telegramBot.executeWithRetry(sendMediaGroup);
        }
    }

    private void sendInfoWithButtons(Artist artist) {

        String infoText;
        try {
            infoText = objectMapper.writeValueAsString(artist);
        } catch (JsonProcessingException e) {
            infoText = String.format("""
                            🆕 **New Artist Suggestion**
                            
                            🎤 **Name:** %s
                            📍 **Region:** %s
                            🎹 **Style:** %s
                            🏷 **Tags:** %s
                            
                            📝 **Bio:**
                            %s
                            """,
                    artist.getName(),
                    artist.getRegion(),
                    artist.getStyle(),
                    artist.getMusicTags(),
                    shortenBio(artist.getBiography())
            );
        }


        SendMessage message = new SendMessage(telegramProperties.adminChatId(), infoText);


        InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();
        inlineKeyboardButtons.add(
                InlineKeyboardButton
                        .builder()
                        .text("✅ Approve")
                        .callbackData("ARTIST:APPROVE:" + artist.getId())
                        .build());
        inlineKeyboardButtons.add(
                InlineKeyboardButton
                        .builder()
                        .text("❌ Reject")
                        .callbackData("ARTIST:REJECT:" + artist.getId())
                        .build());

        InlineKeyboardMarkup markup = InlineKeyboardMarkup
                .builder()
                .keyboardRow(inlineKeyboardButtons)
                .build();

        message.setReplyMarkup(markup);
        telegramBot.executeWithRetry(message);
    }

    public void sendEventForApproval(Event event) {
        if (!telegramProperties.isEnabled()) {
            log.info("Telegram is off (telegram.enabled=false): event {} not sent for approval", event.getId());
            return;
        }

        String fileUrl = s3Service.getFileUrl(event.getEventImageKey());
        if (event.getEventImageKey() == null) return;
        SendPhoto sendPhoto = SendPhoto
                .builder()
                .chatId(telegramProperties.adminChatId())
                .photo(new InputFile(fileUrl))
                .build();
        telegramBot.executeWithRetry(sendPhoto);


        String infoText;
        try {
            infoText = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            String dateStr = event.getDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
            infoText = String.format("""
                            🎫 **NEW EVENT SUGGESTED**
                            
                            %s
                            🏷 **Title:** %s
                            🏙 **City:** %s (%s)
                            📍 **Venue:** %s
                            📅 **Date:** %s
                            
                            🔗 [Ticket Link](%s)
                            """,
                    event.getArtistName(),
                    event.getTitle(),
                    event.getCity(),
                    event.getRegion().getUaName(),
                    event.getVenue(),
                    dateStr,
                    (event.getTicketUrl() != null ? event.getTicketUrl() : "No Link")
            );
        }
        SendMessage message = new SendMessage(telegramProperties.adminChatId(), infoText);
        message.setParseMode("Markdown");
        message.setDisableWebPagePreview(true);

        InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();

        inlineKeyboardButtons.add(
                InlineKeyboardButton
                        .builder()
                        .text("✅ Approve")
                        .callbackData("EVENT:APPROVE:" + event.getId())
                        .build());

        inlineKeyboardButtons.add(
                InlineKeyboardButton
                        .builder()
                        .text("❌ Reject")
                        .callbackData("EVENT:REJECT:" + event.getId())
                        .build());

        InlineKeyboardMarkup markup = InlineKeyboardMarkup
                .builder()
                .keyboardRow(inlineKeyboardButtons)
                .build();

        message.setReplyMarkup(markup);
        telegramBot.executeWithRetry(message);
    }

    private String shortenBio(String bio) {
        if (bio == null) return "";
        return bio.length() > 200 ? bio.substring(0, 200) + "..." : bio;
    }

}
