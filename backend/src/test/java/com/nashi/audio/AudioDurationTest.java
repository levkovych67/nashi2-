package com.nashi.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioDurationTest {

    @Test
    void readsTheLengthOfAnMp3() throws IOException {
        try (var in = getClass().getResourceAsStream("/audio/sample.mp3")) {
            assertThat(AudioDuration.seconds(in.readAllBytes(), "mp3")).isEqualTo(3);
        }
    }

    @Test
    void rejectsBytesThatAreNotAudio() {
        assertThatThrownBy(() -> AudioDuration.seconds(new byte[]{1, 2, 3, 4}, "mp3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
