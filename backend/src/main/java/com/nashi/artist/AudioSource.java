package com.nashi.artist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where an audio clip comes from. */
@Schema(enumAsRef = true)
public enum AudioSource {
    UPLOAD, DEEZER, ITUNES, COMMONS
}
