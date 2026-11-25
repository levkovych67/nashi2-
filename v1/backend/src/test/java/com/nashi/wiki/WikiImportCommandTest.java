package com.nashi.wiki;

import com.nashi.bot.TelegramBot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiImportCommandTest {

    // The bot reads texts from the admin chat only (TelegramBot.isAdminChat, see TelegramChatGuardTest);
    // within it, only these texts start the import.
    @Test
    void recognisesTheCommandText() {
        assertTrue(TelegramBot.isWikiImportText("/wiki_import"));
        assertTrue(TelegramBot.isWikiImportText("/wiki_import@nashi_bot"));
        assertTrue(TelegramBot.isWikiImportText(" /wiki_import "));
        assertFalse(TelegramBot.isWikiImportText("wiki_import"));
        assertFalse(TelegramBot.isWikiImportText("hello"));
        assertFalse(TelegramBot.isWikiImportText("/wiki_importer"));
        assertFalse(TelegramBot.isWikiImportText(null));
    }
}
