package com.nashi.wiki;

/** The url relations of a MusicBrainz artist, as the JSON the web service answers with. */
public interface MusicBrainzSource {

    String get(String mbid);
}
