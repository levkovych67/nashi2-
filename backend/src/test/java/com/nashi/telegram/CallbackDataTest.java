package com.nashi.telegram;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackDataTest {

    private static final String ID = "6650f0c2a1b2c3d4e5f60718";

    @Test
    void callbackEncodeParseRoundTrip() {
        CallbackData data = new CallbackData(ID, Verdict.YES);
        assertThat(data.encode()).isEqualTo("sub:6650f0c2a1b2c3d4e5f60718:y");
        assertThat(CallbackData.parse(data.encode())).contains(data);
        assertThat(CallbackData.parse("sub:" + ID + ":n")).contains(new CallbackData(ID, Verdict.NO));
    }

    @Test
    void callbackRejectsForeignFormats() {
        assertThat(CallbackData.parse("ARTIST:APPROVE:1")).isEmpty();
        assertThat(CallbackData.parse("sub:xyz:y")).isEmpty();
        assertThat(CallbackData.parse("sub:" + ID + ":maybe")).isEmpty();
        assertThat(CallbackData.parse(null)).isEqualTo(Optional.empty());
    }

    @Test
    void callbackFitsTelegramLimit() {
        assertThat(new CallbackData(ID, Verdict.NO).encode().getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(64);
    }
}
