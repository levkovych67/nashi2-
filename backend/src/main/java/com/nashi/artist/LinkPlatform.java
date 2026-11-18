package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Platform of an external link; {@link #group()} follows {@code docs/02-contracts.md} section 2. */
@Schema(enumAsRef = true)
public enum LinkPlatform {
    SPOTIFY(LinkGroup.MUSIC),
    APPLE_MUSIC(LinkGroup.MUSIC),
    DEEZER(LinkGroup.MUSIC),
    YOUTUBE_MUSIC(LinkGroup.MUSIC),
    SOUNDCLOUD(LinkGroup.MUSIC),
    BANDCAMP(LinkGroup.MUSIC),
    YOUTUBE(LinkGroup.VIDEO),
    WIKIPEDIA(LinkGroup.REFERENCE),
    WEBSITE(LinkGroup.REFERENCE),
    INSTAGRAM(LinkGroup.SOCIAL),
    FACEBOOK(LinkGroup.SOCIAL),
    TIKTOK(LinkGroup.SOCIAL),
    TELEGRAM(LinkGroup.SOCIAL),
    X(LinkGroup.SOCIAL),
    OTHER(LinkGroup.SOCIAL);

    private final LinkGroup group;

    LinkPlatform(LinkGroup group) {
        this.group = group;
    }

    public LinkGroup group() {
        return group;
    }
}
