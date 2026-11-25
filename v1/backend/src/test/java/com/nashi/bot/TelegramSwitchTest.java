package com.nashi.bot;

import com.nashi.config.TelegramProperties;
import com.nashi.domain.Artist;
import com.nashi.domain.Event;
import com.nashi.dto.TelegramNewsPostDTO;
import com.nashi.enums.Region;
import com.nashi.service.ArtistApprovalService;
import com.nashi.service.EventApprovalService;
import com.nashi.service.S3Service;
import com.nashi.service.impl.TelegramService;
import com.nashi.service.impl.artist.ArtistVisibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TelegramSwitchTest {

    private static final String IMAGE = "https://example.org/a.jpg";

    private final TelegramClient client = mock(TelegramClient.class);

    @Test
    void disabledBotSendsNothing() throws Exception {
        TelegramBot bot = bot(false);

        bot.sendAdminText("wiki import finished");
        bot.sendNewsToChannel(TelegramNewsPostDTO.builder().caption("news").imageUrl(IMAGE).build());
        bot.sendNewsToChannel(TelegramNewsPostDTO.builder().caption("news").build());
        bot.executeWithRetry(SendMessage.builder().chatId("1").text("hi").build());
        bot.executeWithRetry(SendPhoto.builder().chatId("1").photo(new InputFile(IMAGE)).build());
        bot.executeWithRetry(SendMediaGroup.builder().chatId("1").medias(List.of(
                InputMediaPhoto.builder().media(IMAGE).build(),
                InputMediaPhoto.builder().media(IMAGE).build())).build());
        bot.executeWithRetry(SendAudio.builder().chatId("1")
                .audio(new InputFile(new ByteArrayInputStream(new byte[0]), "a.mp3")).build());

        verifyNoInteractions(client);
    }

    @Test
    void botIsOnWhenTheSwitchIsMissing() throws Exception {
        TelegramBot bot = bot(null);

        bot.sendAdminText("wiki import finished");

        verify(client).execute(any(SendMessage.class));
    }

    @Test
    void disabledServiceTouchesNeitherS3NorTheBot() {
        TelegramBot bot = mock(TelegramBot.class);
        S3Service s3 = mock(S3Service.class);
        TelegramService service = new TelegramService(bot, s3, properties(false));

        service.sendArtistForApproval(Artist.builder().id("a1").name("Гурт")
                .imageKeys(List.of("artists/a.jpg")).demoTrackKeys(List.of("artists/t.mp3")).build());
        service.sendEventForApproval(Event.builder().id("e1").title("Концерт").region(Region.LVIV)
                .dateTime(LocalDateTime.of(2026, 11, 7, 19, 0)).eventImageKey("events/e.jpg").build());

        verifyNoInteractions(bot, s3);
    }

    private TelegramBot bot(Boolean enabled) {
        return new TelegramBot(client, mock(ArtistApprovalService.class), mock(EventApprovalService.class),
                mock(ArtistVisibilityService.class), properties(enabled), mock(ApplicationEventPublisher.class));
    }

    private static TelegramProperties properties(Boolean enabled) {
        return new TelegramProperties("test-token", "-100", "-200", enabled);
    }
}
