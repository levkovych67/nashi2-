package com.nashi.artist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    static Stream<Arguments> namesJson() {
        return TestVectors.read("names.json").stream().map(v -> Arguments.of(v.get("input"), v.get("slug")));
    }

    @ParameterizedTest
    @MethodSource("namesJson")
    void slugMatchesVector(String in, String expected) {
        assertThat(SlugGenerator.slugify(in)).isEqualTo(expected);
    }

    @Test
    void slugOfOnlySymbolsFallsBackToArtist() {
        assertThat(SlugGenerator.slugify("!!!")).isEqualTo("artist");
        assertThat(SlugGenerator.slugify(null)).isEqualTo("artist");
        assertThat(SlugGenerator.slugify("ь")).isEqualTo("artist");
    }

    @Test
    void longNamesAreCutAtAWordBoundary() {
        String name = "слово ".repeat(30).trim();
        String slug = SlugGenerator.slugify(name);

        assertThat(slug.length()).isLessThanOrEqualTo(80);
        assertThat(slug).endsWith("slovo").doesNotEndWith("-");
        assertThat(slug).matches("(slovo-)*slovo");
    }

    @Test
    void aSingleLongWordIsCutHard() {
        assertThat(SlugGenerator.slugify("а".repeat(200))).hasSize(80);
    }
}
