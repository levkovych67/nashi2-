package com.nashi.musician;

import com.nashi.artist.AudioSource;
import com.nashi.artist.ImageCredit;
import com.nashi.artist.LifeDates;
import com.nashi.artist.LinkGroup;
import com.nashi.artist.LinkPlatform;
import com.nashi.artist.MusicianKind;
import com.nashi.artist.OriginBasis;
import com.nashi.artist.PinPrecision;
import com.nashi.artist.TextSource;
import com.nashi.artist.Video;
import com.nashi.artist.Work;
import com.nashi.geo.Region;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * {@code MusicianDetails} of the contract ({@code docs/02-contracts.md} 2.6). Nullable fields are written as
 * {@code null}, never omitted. {@code origin} is {@code null} for a musician without a map pin.
 */
public record MusicianDetails(String id, String slug, String name, MusicianKind kind, String tagline, String summary,
                              String description, TextSource textSource, List<Image> images, OriginDto origin,
                              LifeDates dates, List<String> genres, Materials materials, List<LinkDto> links,
                              String youtubeChannelUrl, long likesCount, boolean hasAudio, Instant updatedAt) {

    /** {@code Image}: stored uploads resolve to public URLs, external (Commons) images keep their own. */
    public record Image(String url, String thumbUrl, String alt, ImageCredit credit) {
    }

    /** {@code Origin}: {@code lat}/{@code lon} are the pin, not the settlement centre. */
    @Schema(name = "Origin")
    public record OriginDto(String placeId, String placeName, Region region, String regionName, OriginBasis basis,
                            PinPrecision precision, double lat, double lon) {
    }

    public record Materials(List<ClipSummary> clips, List<Work> works, List<Video> videos) {
    }

    /** A clip without any playback URL (those are issued by the fragment endpoint). */
    public record ClipSummary(String clipId, String title, AudioSource source, int durationSec, String externalUrl) {
    }

    @Schema(name = "Link")
    public record LinkDto(LinkPlatform platform, LinkGroup group, String url) {
    }
}
