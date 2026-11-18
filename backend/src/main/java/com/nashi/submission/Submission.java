package com.nashi.submission;

import com.nashi.artist.MusicianKind;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A suggested musician ({@code docs/02-contracts.md} section 3, {@code submissions}). The fields of the request are
 * kept as sent, except {@code faxNumber} (never stored) and {@code consent} (becomes {@code consentAt}). The client
 * IP is only kept as {@code ipHash}. {@code imageKeys} lists both the full and the thumbnail key of every photo;
 * the {@code submissions/} prefix is private. {@code telegram}, {@code moderationLog}, {@code artistId} and
 * {@code decidedAt} are filled by moderation. {@code version} makes {@code save} with a pre-assigned id an insert.
 */
@Document("submissions")
@Data
@Builder
public class Submission {

    @Id
    private String id;
    @Version
    private Long version;

    private SubmissionStatus status;

    private String name;
    private MusicianKind kind;
    private String originPlaceId;
    private String birthPlaceId;
    private String summary;
    private List<String> genres;
    private List<String> links;
    private String youtubeChannelUrl;
    private String wikipediaUrl;
    private String contact;
    private Instant consentAt;

    private List<String> imageKeys;
    private String ipHash;
    private String duplicateOfArtistId;

    private TelegramRefs telegram;
    private List<ModerationEntry> moderationLog;
    private String artistId;

    @CreatedDate
    private Instant createdAt;
    private Instant decidedAt;
}
