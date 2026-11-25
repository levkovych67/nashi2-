package com.nashi.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLink {
    
    private Platform platform;
    private String url;

    public enum Platform {
        INSTAGRAM, FACEBOOK, SPOTIFY, APPLE_MUSIC, YOUTUBE, SOUNDCLOUD, TIKTOK, TELEGRAM, WEBSITE, OTHER, WIKI,
        X, DEEZER, BANDCAMP, DISCOGS, MUSICBRAINZ, LASTFM, COMMONS, WIKISOURCE, GOODREADS, OPEN_LIBRARY, ARTSY,
        WIKIDATA,
        /** Encyclopedia of Modern Ukraine, esu.com.ua. */
        ESU,
        /** Chtyvo electronic library, chtyvo.org.ua. */
        CHTYVO,
        WIKIART
    }
}