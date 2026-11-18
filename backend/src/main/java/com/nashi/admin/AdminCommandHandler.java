package com.nashi.admin;

import com.nashi.artist.ArtistVisibilityService;
import com.nashi.telegram.ChatAdmins;
import com.nashi.telegram.Html;
import com.nashi.telegram.CommandHandler;
import com.nashi.telegram.TelegramGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

/** Runs the administrators' commands from the moderation group; anyone else is ignored silently. */
@Component
public class AdminCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminCommandHandler.class);

    static final String HELP = "Команди: /hide &lt;slug або Q-id&gt;, /show &lt;slug або Q-id&gt;, /stats, "
            + "/wiki_import, /audio_resolve, /ping";

    private final ChatAdmins admins;
    private final TelegramGateway gateway;
    private final ArtistVisibilityService visibility;
    private final StatsService stats;
    private final ObjectProvider<JobTrigger> wikiImport;
    private final ObjectProvider<JobTrigger> audioResolve;

    public AdminCommandHandler(ChatAdmins admins, TelegramGateway gateway, ArtistVisibilityService visibility,
                               StatsService stats,
                               @Qualifier("wikiImport") ObjectProvider<JobTrigger> wikiImport,
                               @Qualifier("audioResolve") ObjectProvider<JobTrigger> audioResolve) {
        this.admins = admins;
        this.gateway = gateway;
        this.visibility = visibility;
        this.stats = stats;
        this.wikiImport = wikiImport;
        this.audioResolve = audioResolve;
    }

    @Override
    public void handle(Message message) {
        User from = message.getFrom();
        if (from == null || !admins.contains(from.getId())) {
            log.debug("Ignoring command from non-admin {}", from == null ? null : from.getId());
            return;
        }
        try {
            gateway.sendText(reply(message.getText()));
        } catch (RuntimeException e) {
            log.error("Admin command failed", e);
            try {
                gateway.sendText("Не вдалося виконати команду, дивіться логи.");
            } catch (RuntimeException ignored) {
                log.error("Could not report the failure to the chat", ignored);
            }
        }
    }

    private String reply(String text) {
        Optional<AdminCommand> parsed = AdminCommand.parse(text);
        if (parsed.isEmpty()) {
            return HELP;
        }
        AdminCommand command = parsed.get();
        return switch (command.action()) {
            case HIDE -> command.argument().isEmpty() ? usage(command) : visibility.hide(command.argument());
            case SHOW -> command.argument().isEmpty() ? usage(command) : visibility.show(command.argument());
            case STATS -> stats.summary();
            case WIKI_IMPORT -> start(wikiImport, "Імпорт уже йде", "Запущено імпорт з Вікіпедії…");
            case AUDIO_RESOLVE -> start(audioResolve, "Пошук аудіо вже йде", "Запущено пошук аудіо…");
            case PING -> "alive";
        };
    }

    private static String usage(AdminCommand command) {
        return Html.escape(command.usage());
    }

    private static String start(ObjectProvider<JobTrigger> job, String alreadyRunning, String started) {
        JobTrigger trigger = job.getIfAvailable();
        if (trigger == null) {
            return "Ще не налаштовано";
        }
        return trigger.startInBackground() ? started : alreadyRunning;
    }
}
