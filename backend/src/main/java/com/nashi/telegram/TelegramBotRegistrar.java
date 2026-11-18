package com.nashi.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/** Starts long polling with the router when {@code nashi.telegram.polling-enabled} is true. */
@Component
public class TelegramBotRegistrar implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrar.class);

    private final TelegramProperties props;
    private final TelegramUpdateRouter router;
    private TelegramBotsLongPollingApplication application;

    public TelegramBotRegistrar(TelegramProperties props, TelegramUpdateRouter router) {
        this.props = props;
        this.router = router;
    }

    @Override
    public synchronized void start() {
        if (!props.pollingEnabled()) {
            log.info("Telegram polling off");
            return;
        }
        TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
        try {
            app.registerBot(props.botToken(), router);
        } catch (TelegramApiException e) {
            closeQuietly(app);
            throw new IllegalStateException("Could not start Telegram long polling", e);
        }
        application = app;
        log.info("Telegram polling started");
    }

    @Override
    public synchronized void stop() {
        if (application != null) {
            closeQuietly(application);
            application = null;
            log.info("Telegram polling stopped");
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return application != null;
    }

    private static void closeQuietly(TelegramBotsLongPollingApplication app) {
        try {
            app.close();
        } catch (Exception e) {
            log.warn("Closing Telegram polling failed: {}", e.getMessage());
        }
    }
}
