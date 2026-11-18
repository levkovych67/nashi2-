package com.nashi.telegram;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAdminsTest {

    private final TelegramGateway gateway = mock(TelegramGateway.class);
    private final AtomicLong nanos = new AtomicLong();
    private final ChatAdmins admins = new ChatAdmins(gateway, nanos::get);

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    @Test
    void chatAdminsCachedForFiveMinutes() {
        when(gateway.chatAdminIds()).thenReturn(Set.of(7L));

        assertThat(admins.contains(7L)).isTrue();
        advance(Duration.ofMinutes(4));
        assertThat(admins.contains(8L)).isFalse();
        verify(gateway, times(1)).chatAdminIds();

        advance(Duration.ofMinutes(2));
        assertThat(admins.contains(7L)).isTrue();
        verify(gateway, times(2)).chatAdminIds();
    }

    @Test
    void chatAdminsFallsBackToLastKnownOnError() {
        when(gateway.chatAdminIds()).thenReturn(Set.of(7L)).thenThrow(new TelegramGatewayException("down", null));

        assertThat(admins.contains(7L)).isTrue();
        advance(Duration.ofMinutes(6));
        assertThat(admins.contains(7L)).isTrue();
        assertThat(admins.contains(9L)).isFalse();
    }

    @Test
    void chatAdminsIsFalseWhenNothingWasEverLoaded() {
        when(gateway.chatAdminIds()).thenThrow(new TelegramGatewayException("down", null));
        assertThat(admins.contains(7L)).isFalse();
    }
}
