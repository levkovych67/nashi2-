package com.nashi.telegram;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
class TelegramConfig {

    /** Building the client opens no connection, so local and test contexts start without network. */
    @Bean
    TelegramClient telegramClient(TelegramProperties props) {
        return new OkHttpTelegramClient(props.botToken());
    }
}
