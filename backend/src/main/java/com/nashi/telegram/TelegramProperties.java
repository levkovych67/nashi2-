package com.nashi.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bot settings under {@code nashi.telegram.*}.
 *
 * @param botToken       bot API token; a secret, never logged
 * @param pollingEnabled receive updates by long polling; off unless set (production turns it on)
 * @param sendingEnabled post to Telegram; off unless set, so local and test runs never reach the real chat
 * @param moderation     the moderation chat
 */
@ConfigurationProperties("nashi.telegram")
public record TelegramProperties(
        String botToken,
        @DefaultValue("false") boolean pollingEnabled,
        @DefaultValue("false") boolean sendingEnabled,
        @DefaultValue Moderation moderation) {

    /**
     * @param chatId         the only chat the bot talks to and listens to
     * @param threadId       forum topic for outgoing messages; null for a plain group
     * @param approverPolicy who may press the buttons
     */
    public record Moderation(
            @DefaultValue("0") long chatId,
            Integer threadId,
            @DefaultValue("CHAT_ADMINS") ApproverPolicy approverPolicy) {
    }
}
