package com.nashi.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelegramBotRegistrarTest {

    @Test
    void doesNotPollWhenDisabled() {
        TelegramProperties props = new TelegramProperties("t", false, false,
                new TelegramProperties.Moderation(1L, null, ApproverPolicy.CHAT_ADMINS));
        TelegramBotRegistrar registrar = new TelegramBotRegistrar(props, mock(TelegramUpdateRouter.class));

        registrar.start();

        assertThat(registrar.isRunning()).isFalse();
        registrar.stop();
    }
}
