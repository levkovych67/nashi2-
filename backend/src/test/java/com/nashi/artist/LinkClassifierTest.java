package com.nashi.artist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LinkClassifierTest {

    static Stream<Arguments> linksJson() {
        return TestVectors.read("links.json").stream()
                .map(v -> Arguments.of(v.get("url"), LinkPlatform.valueOf(v.get("platform"))));
    }

    @ParameterizedTest
    @MethodSource("linksJson")
    void classifyMatchesVector(String url, LinkPlatform expected) {
        assertThat(LinkClassifier.classify(url)).isEqualTo(expected);
    }

    @Test
    void nullAndGarbageAreOther() {
        assertThat(LinkClassifier.classify(null)).isEqualTo(LinkPlatform.OTHER);
        assertThat(LinkClassifier.classify("not a url")).isEqualTo(LinkPlatform.OTHER);
    }

    @Test
    void lookAlikeHostsAreNotMatched() {
        assertThat(LinkClassifier.classify("https://notspotify.com/x")).isEqualTo(LinkPlatform.WEBSITE);
        assertThat(LinkClassifier.classify("https://spotify.com.evil.example/x")).isEqualTo(LinkPlatform.WEBSITE);
    }

    @Test
    void groupsFollowContract() {
        assertThat(LinkPlatform.SPOTIFY.group()).isEqualTo(LinkGroup.MUSIC);
        assertThat(LinkPlatform.BANDCAMP.group()).isEqualTo(LinkGroup.MUSIC);
        assertThat(LinkPlatform.YOUTUBE.group()).isEqualTo(LinkGroup.VIDEO);
        assertThat(LinkPlatform.WIKIPEDIA.group()).isEqualTo(LinkGroup.REFERENCE);
        assertThat(LinkPlatform.WEBSITE.group()).isEqualTo(LinkGroup.REFERENCE);
        assertThat(LinkPlatform.INSTAGRAM.group()).isEqualTo(LinkGroup.SOCIAL);
        assertThat(LinkPlatform.OTHER.group()).isEqualTo(LinkGroup.SOCIAL);
    }
}
