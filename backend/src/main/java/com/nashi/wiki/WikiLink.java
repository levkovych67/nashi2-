package com.nashi.wiki;

import com.nashi.artist.LinkPlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

/** A link found on Wikidata or MusicBrainz, before it is mapped to the API's {@link LinkPlatform}. */
@Getter
@Builder
@AllArgsConstructor
public class WikiLink {

    private final Platform platform;
    private final String url;

    /** Sources the import knows; only some of them have a counterpart in {@link LinkPlatform}. */
    public enum Platform {
        INSTAGRAM, FACEBOOK, SPOTIFY, APPLE_MUSIC, YOUTUBE, SOUNDCLOUD, TIKTOK, TELEGRAM, WEBSITE, OTHER, WIKI,
        X, DEEZER, BANDCAMP, DISCOGS, MUSICBRAINZ, LASTFM, COMMONS, WIKISOURCE, GOODREADS, OPEN_LIBRARY, ARTSY,
        WIKIDATA,
        /** Encyclopedia of Modern Ukraine, esu.com.ua. */
        ESU,
        /** Chtyvo electronic library, chtyvo.org.ua. */
        CHTYVO,
        WIKIART;

        /**
         * The platform of the API for this one: same name where there is one, WIKI is WIKIPEDIA, DISCOGS, LASTFM and
         * ESU are OTHER; empty (the link is dropped) for WIKIDATA, MUSICBRAINZ, COMMONS, WIKISOURCE, OPEN_LIBRARY,
         * GOODREADS, CHTYVO, ARTSY and WIKIART.
         */
        public Optional<LinkPlatform> toV2() {
            return switch (this) {
                case INSTAGRAM -> Optional.of(LinkPlatform.INSTAGRAM);
                case FACEBOOK -> Optional.of(LinkPlatform.FACEBOOK);
                case SPOTIFY -> Optional.of(LinkPlatform.SPOTIFY);
                case APPLE_MUSIC -> Optional.of(LinkPlatform.APPLE_MUSIC);
                case YOUTUBE -> Optional.of(LinkPlatform.YOUTUBE);
                case SOUNDCLOUD -> Optional.of(LinkPlatform.SOUNDCLOUD);
                case TIKTOK -> Optional.of(LinkPlatform.TIKTOK);
                case TELEGRAM -> Optional.of(LinkPlatform.TELEGRAM);
                case WEBSITE -> Optional.of(LinkPlatform.WEBSITE);
                case X -> Optional.of(LinkPlatform.X);
                case DEEZER -> Optional.of(LinkPlatform.DEEZER);
                case BANDCAMP -> Optional.of(LinkPlatform.BANDCAMP);
                case WIKI -> Optional.of(LinkPlatform.WIKIPEDIA);
                case OTHER, DISCOGS, LASTFM, ESU -> Optional.of(LinkPlatform.OTHER);
                case WIKIDATA, MUSICBRAINZ, COMMONS, WIKISOURCE, OPEN_LIBRARY, GOODREADS, CHTYVO, ARTSY, WIKIART ->
                        Optional.empty();
            };
        }
    }
}
