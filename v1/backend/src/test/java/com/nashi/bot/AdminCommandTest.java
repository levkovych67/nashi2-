package com.nashi.bot;

import com.nashi.config.TelegramProperties;
import com.nashi.service.ArtistApprovalService;
import com.nashi.service.EventApprovalService;
import com.nashi.service.impl.artist.ArtistVisibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCommandTest {

    private static final String ADMIN = "-100500";

    @Test
    void parsesHideAndShowWithSlugOrQid() {
        assertEquals(Optional.of(new AdminCommand(AdminCommand.Action.HIDE, "okean-elzy")),
                AdminCommand.parse("/hide okean-elzy"));
        assertEquals(Optional.of(new AdminCommand(AdminCommand.Action.SHOW, "Q1328549")),
                AdminCommand.parse("/show Q1328549"));
        assertEquals(Optional.of(new AdminCommand(AdminCommand.Action.HIDE, "океан-ельзи")),
                AdminCommand.parse(" /hide@nashi_bot   океан-ельзи "));
        assertEquals(Optional.of(new AdminCommand(AdminCommand.Action.SHOW, "q42")),
                AdminCommand.parse("/show q42"));
        // No argument: still the command, so the bot can say how to use it.
        assertEquals(Optional.of(new AdminCommand(AdminCommand.Action.HIDE, "")),
                AdminCommand.parse("/hide"));

        assertTrue(AdminCommand.parse("/hidden okean-elzy").isEmpty());
        assertTrue(AdminCommand.parse("hide okean-elzy").isEmpty());
        assertTrue(AdminCommand.parse("/wiki_import").isEmpty());
        assertTrue(AdminCommand.parse(null).isEmpty());
    }

    @Test
    void ignoresCommandsFromOtherChats() {
        ArtistVisibilityService visibility = mock(ArtistVisibilityService.class);
        when(visibility.hide("okean-elzy")).thenReturn("Приховано");
        when(visibility.show("Q1328549")).thenReturn("Знову показано");
        TelegramClient client = mock(TelegramClient.class);
        TelegramBot bot = new TelegramBot(client, mock(ArtistApprovalService.class), mock(EventApprovalService.class),
                visibility, new TelegramProperties("test-token", ADMIN, "-200", true),
                mock(ApplicationEventPublisher.class));

        bot.consume(text(777L, "/hide okean-elzy"));
        bot.consume(text(777L, "/show Q1328549"));
        verifyNoInteractions(visibility, client);

        bot.consume(text(Long.parseLong(ADMIN), "/hide okean-elzy"));
        bot.consume(text(Long.parseLong(ADMIN), "/show Q1328549"));
        verify(visibility).hide("okean-elzy");
        verify(visibility).show("Q1328549");

        assertTrue(TelegramBot.isAdminChat(ADMIN, ADMIN));
        assertTrue(TelegramBot.isAdminChat(ADMIN, " " + ADMIN + " "));
        assertFalse(TelegramBot.isAdminChat("777", ADMIN));
        assertFalse(TelegramBot.isAdminChat(null, ADMIN));
        assertFalse(TelegramBot.isAdminChat(ADMIN, null));
    }

    private static Update text(long chatId, String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "supergroup"));
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
