package com.nashi.artist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NamesTest {

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of("Кам’янець", "камянець"),
                Arguments.of("Кам'янець-Подільський", "камянець-подільський"),
                Arguments.of("Кам‘янка", "камянка"),
                Arguments.of("Камʼянське", "камянське"),
                Arguments.of("Кам`янка", "камянка"),
                Arguments.of("БРО\u0301ДИ", "броди"),
                Arguments.of("«Океан Ельзи»", "океан ельзи"),
                Arguments.of("„Пісня“ ”про“ \"щось\"", "пісня про щось"),
                Arguments.of("  Ї\u0301жак   Згорани  ", "їжак згорани"),
                Arguments.of("The Hardkiss", "the hardkiss"));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void normalizes(String input, String expected) {
        assertThat(Names.normalize(input)).isEqualTo(expected);
    }

    @Test
    void composesDecomposedLettersSoTheyMatchStoredNames() {
        // "й" typed as "и" + combining breve (NFD) must equal the precomposed "й" of Wikidata labels
        assertThat(Names.normalize("Бі\u0438\u0306")).isEqualTo(Names.normalize("Бій"));
    }

    @Test
    void collapsesTabsAndNewlines() {
        assertThat(Names.normalize("Біла\t\nЦерква")).isEqualTo("біла церква");
    }

    @Test
    void nullAndBlankBecomeEmpty() {
        assertThat(Names.normalize(null)).isEmpty();
        assertThat(Names.normalize("  ’ ")).isEmpty();
    }
}
