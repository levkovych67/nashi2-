package com.nashi.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The validator itself: it accepts what the contract allows and catches type, null and format mismatches. */
class OpenApiContractTest {

    private static final String CREDIT = """
            {"author": "Автор", "license": "CC BY-SA 4.0", "licenseUrl": null,
             "sourceUrl": "https://commons.wikimedia.org/wiki/File:X.jpg"}""";

    private static String details(String summary, String credit, String updatedAt) {
        return """
                {"id": "a1", "slug": "s", "name": "Ім'я", "kind": "PERSON", "tagline": null, "summary": %s,
                 "description": null, "textSource": null,
                 "images": [{"url": "https://img/full.jpg", "thumbUrl": "https://img/thumb.jpg", "alt": "alt",
                             "credit": %s}],
                 "origin": null,
                 "dates": {"birth": "1872-09-23", "death": null, "activeFrom": null, "activeTo": null},
                 "genres": [], "materials": {"clips": [], "works": [], "videos": []}, "links": [],
                 "youtubeChannelUrl": null, "likesCount": 3, "hasAudio": false, "updatedAt": %s}
                """.formatted(summary, credit, updatedAt);
    }

    @Test
    void acceptsAValidBody() {
        assertThatCode(() -> OpenApiContract.assertSchema("MusicianDetails",
                details("\"Опис\"", CREDIT, "\"2026-11-17T10:15:00Z\""))).doesNotThrowAnyException();
    }

    @Test
    void summaryAndCreditAuthorAndLicenseMayBeNull() {
        String credit = """
                {"author": null, "license": null, "licenseUrl": null,
                 "sourceUrl": "https://commons.wikimedia.org/wiki/File:X.jpg"}""";
        assertThatCode(() -> OpenApiContract.assertSchema("MusicianDetails",
                details("null", credit, "\"2026-11-17T10:15:00Z\""))).doesNotThrowAnyException();
    }

    @Test
    void summaryAndCreditFieldsAreStillRequired() {
        assertThatThrownBy(() -> OpenApiContract.assertSchema("MusicianDetails",
                details("\"Опис\"", CREDIT, "\"2026-11-17T10:15:00Z\"").replace("\"summary\": \"Опис\",", "")))
                .hasMessageContaining("summary");
        assertThatThrownBy(() -> OpenApiContract.assertSchema("ImageCredit",
                "{\"license\": null, \"sourceUrl\": \"https://commons.wikimedia.org/wiki/File:X.jpg\"}"))
                .hasMessageContaining("author");
    }

    @Test
    void catchesWrongTypesNullsAndFormats() {
        assertThatThrownBy(() -> OpenApiContract.assertSchema("MusicianDetails",
                details("\"Опис\"", CREDIT, "\"yesterday\""))).hasMessageContaining("updatedAt");
        assertThatThrownBy(() -> OpenApiContract.assertSchema("MusicianDetails",
                details("\"Опис\"", CREDIT, "\"2026-11-17T10:15:00Z\"").replace("\"likesCount\": 3",
                        "\"likesCount\": \"3\""))).hasMessageContaining("likesCount");
        assertThatThrownBy(() -> OpenApiContract.assertSchema("MusicianDetails",
                details("\"Опис\"", CREDIT, "\"2026-11-17T10:15:00Z\"").replace("\"name\": \"Ім'я\"",
                        "\"name\": null"))).hasMessageContaining("name");
        assertThatThrownBy(() -> OpenApiContract.assertSchema("Link",
                "{\"platform\": \"SPOTIFY\", \"group\": \"MUSIC\", \"url\": \"not a uri\"}"))
                .hasMessageContaining("url");
    }
}
