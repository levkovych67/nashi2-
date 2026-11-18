package com.nashi.musician;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nashi.artist.MusicianKind;
import com.nashi.geo.Region;

/** {@code MusicianListItem} of the contract; {@code thumbUrl} is absent (not null) when the musician has no image. */
public record MusicianListItem(String id, String slug, String name, MusicianKind kind,
                               @JsonInclude(JsonInclude.Include.NON_NULL) String thumbUrl,
                               String placeName, Region region, long likesCount, boolean hasAudio) {
}
