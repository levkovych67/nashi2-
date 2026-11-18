package com.nashi.telegram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** Moderation chat administrators, cached for five minutes. On an API error the last known set is used. */
@Component
public class ChatAdmins {

    static final Duration TTL = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(ChatAdmins.class);

    private final TelegramGateway gateway;
    private final Cache<String, Set<Long>> cache;
    private volatile Set<Long> lastKnown;

    @Autowired
    public ChatAdmins(TelegramGateway gateway) {
        this(gateway, Ticker.systemTicker());
    }

    ChatAdmins(TelegramGateway gateway, Ticker ticker) {
        this.gateway = gateway;
        this.cache = Caffeine.newBuilder().expireAfterWrite(TTL).ticker(ticker).build();
    }

    public boolean contains(long userId) {
        Set<Long> admins;
        try {
            admins = cache.get("admins", key -> gateway.chatAdminIds());
            lastKnown = admins;
        } catch (RuntimeException e) {
            admins = lastKnown;
            log.warn("Could not load chat administrators, using {} value: {}",
                    admins == null ? "no" : "the last known", e.getMessage());
        }
        return admins != null && admins.contains(userId);
    }
}
