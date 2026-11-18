package com.nashi.admin;

import com.nashi.artist.ArtistVisibilityService;
import com.nashi.migration.V1MigrationJob;
import com.nashi.telegram.ChatAdmins;
import com.nashi.telegram.TelegramGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AdminCommandHandlerTest {

    private final ChatAdmins admins = mock(ChatAdmins.class);
    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final ArtistVisibilityService visibility = mock(ArtistVisibilityService.class);
    private final StatsService stats = mock(StatsService.class);
    private final ObjectProvider<JobTrigger> wiki = mock(ObjectProvider.class);
    private final ObjectProvider<JobTrigger> audio = mock(ObjectProvider.class);
    private final ObjectProvider<V1MigrationJob> migration = mock(ObjectProvider.class);
    private AdminCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AdminCommandHandler(admins, gateway, visibility, stats, wiki, audio, migration);
        when(admins.contains(1L)).thenReturn(true);
    }

    private static Message message(long userId, String text) {
        Message m = mock(Message.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(m.getFrom()).thenReturn(user);
        when(m.getText()).thenReturn(text);
        return m;
    }

    @Test
    void nonAdminCommandIgnoredSilently() {
        handler.handle(message(2L, "/hide x"));
        verifyNoInteractions(gateway, visibility);
    }

    @Test
    void pingAnswersAlive() {
        handler.handle(message(1L, "/ping@nashi_bot"));
        verify(gateway).sendText("alive");
    }

    @Test
    void hideRequiresArgument() {
        handler.handle(message(1L, "/hide"));
        verify(gateway).sendText("Використання: /hide &lt;slug або Q-id&gt;");
        verify(visibility, never()).hide(anyString());
    }

    @Test
    void hideByWikidataIdDelegates() {
        when(visibility.hide("Q2017541")).thenReturn("Приховано: X");
        handler.handle(message(1L, "/hide Q2017541"));
        verify(visibility).hide("Q2017541");
        verify(gateway).sendText("Приховано: X");
    }

    @Test
    void showDelegates() {
        when(visibility.show("a")).thenReturn("Знову показано: A");
        handler.handle(message(1L, "/show a"));
        verify(gateway).sendText("Знову показано: A");
    }

    @Test
    void statsFormat() {
        String summary = "Музикантів на карті: 3 · без піна: 1 · з аудіо: 2 · заявок на модерації: 4 · лайків за весь час: 9";
        when(stats.summary()).thenReturn(summary);
        handler.handle(message(1L, "/stats"));
        verify(gateway).sendText(summary);
    }

    @Test
    void unknownCommandAnswersHelp() {
        handler.handle(message(1L, "/foo"));
        verify(gateway).sendText(AdminCommandHandler.HELP);
    }

    @Test
    void wikiImportAlreadyRunningAnswers() {
        JobTrigger job = mock(JobTrigger.class);
        when(job.startInBackground()).thenReturn(false);
        when(wiki.getIfAvailable()).thenReturn(job);
        handler.handle(message(1L, "/wiki_import"));
        verify(gateway).sendText("Імпорт уже йде");
    }

    @Test
    void wikiImportStarts() {
        JobTrigger job = mock(JobTrigger.class);
        when(job.startInBackground()).thenReturn(true);
        when(wiki.getIfAvailable()).thenReturn(job);
        handler.handle(message(1L, "/wiki_import"));
        verify(gateway).sendText("Запущено імпорт з Вікіпедії…");
    }

    @Test
    void jobsNotConfiguredYet() {
        handler.handle(message(1L, "/wiki_import"));
        handler.handle(message(1L, "/audio_resolve"));
        verify(gateway, org.mockito.Mockito.times(2)).sendText("Ще не налаштовано");
    }

    @Test
    void audioResolveAlreadyRunningAnswers() {
        JobTrigger job = mock(JobTrigger.class);
        when(job.startInBackground()).thenReturn(false);
        when(audio.getIfAvailable()).thenReturn(job);
        handler.handle(message(1L, "/audio_resolve"));
        verify(gateway).sendText("Пошук аудіо вже йде");
    }

    @Test
    void failureAnsweredWithoutExceptionText() {
        when(stats.summary()).thenThrow(new IllegalStateException("secret mongo uri"));
        handler.handle(message(1L, "/stats"));
        verify(gateway).sendText("Не вдалося виконати команду, дивіться логи.");
    }

    @Test
    void adminCommandRequiresDryOrRun() {
        V1MigrationJob job = mock(V1MigrationJob.class);
        when(job.isConfigured()).thenReturn(true);
        when(migration.getIfAvailable()).thenReturn(job);

        handler.handle(message(1L, "/migrate_v1"));
        handler.handle(message(1L, "/migrate_v1 now"));

        verify(gateway, org.mockito.Mockito.times(2)).sendText("Використання: /migrate_v1 dry|run");
        verify(job, never()).startInBackground(org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void migrateStartsDryAndRealRuns() {
        V1MigrationJob job = mock(V1MigrationJob.class);
        when(job.isConfigured()).thenReturn(true);
        when(job.startInBackground(true)).thenReturn(true);
        when(job.startInBackground(false)).thenReturn(false);
        when(migration.getIfAvailable()).thenReturn(job);

        handler.handle(message(1L, "/migrate_v1 dry"));
        handler.handle(message(1L, "/migrate_v1 run"));

        verify(job).startInBackground(true);
        verify(job).startInBackground(false);
        verify(gateway).sendText("Запущено пробну міграцію з V1 (нічого не записується)…");
        verify(gateway).sendText("Міграція вже йде");
    }

    @Test
    void migrateNotConfigured() {
        handler.handle(message(1L, "/migrate_v1 run"));
        V1MigrationJob job = mock(V1MigrationJob.class);
        when(job.isConfigured()).thenReturn(false);
        when(migration.getIfAvailable()).thenReturn(job);
        handler.handle(message(1L, "/migrate_v1 dry"));

        verify(gateway, org.mockito.Mockito.times(2)).sendText("Міграція не налаштована");
        verify(job, never()).startInBackground(org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void migrateIgnoredForNonAdmin() {
        handler.handle(message(2L, "/migrate_v1 run"));
        verifyNoInteractions(gateway, migration);
    }
}
