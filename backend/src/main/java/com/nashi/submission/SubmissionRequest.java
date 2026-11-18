package com.nashi.submission;

import com.nashi.artist.MusicianKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The {@code data} part of {@code POST /api/v2/submissions} ({@code docs/02-contracts.md} 2.10). These annotations
 * hold the single-field rules; {@link SubmissionValidator} adds the rules that look at several fields or at storage.
 * Every free-text field has an upper bound ({@value #MAX_URL} for URLs, {@value #MAX_PLACE_ID} for place ids) so a
 * request cannot store or send arbitrarily long strings. {@code faxNumber} is the bot trap and is not validated.
 */
public record SubmissionRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @NotNull MusicianKind kind,
        @NotBlank @Size(max = MAX_PLACE_ID) String originPlaceId,
        @Size(max = MAX_PLACE_ID) String birthPlaceId,
        @NotNull @Size(min = 30, max = 1000) String summary,
        @Size(max = 5) List<@NotNull @Size(min = 2, max = 40) String> genres,
        @NotNull @Size(min = 1, max = 10) List<@NotNull @Size(max = MAX_URL) String> links,
        @Size(max = MAX_URL) String youtubeChannelUrl,
        @Size(max = MAX_URL) String wikipediaUrl,
        @Size(max = 200) String contact,
        @NotNull @AssertTrue Boolean consent,
        String faxNumber) {

    public static final int MAX_URL = 2048;
    public static final int MAX_PLACE_ID = 32;
}
