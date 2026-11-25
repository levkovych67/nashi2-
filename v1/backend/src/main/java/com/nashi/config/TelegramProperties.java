package com.nashi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled {@code telegram.enabled}; false turns every outgoing Telegram call into a logged no-op.
 *                Missing means on. Long polling has its own switch, {@code telegrambots.enabled}, read by the starter.
 */
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String adminChatId,
        String channelId,
        Boolean enabled
) {

    public boolean isEnabled() {
        return !Boolean.FALSE.equals(enabled);
    }
}
