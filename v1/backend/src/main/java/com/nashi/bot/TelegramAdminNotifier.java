package com.nashi.bot;

import com.nashi.wiki.AdminNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramAdminNotifier implements AdminNotifier {

    private final TelegramBot telegramBot;

    @Override
    public void send(String text) {
        telegramBot.sendAdminText(text);
    }
}
