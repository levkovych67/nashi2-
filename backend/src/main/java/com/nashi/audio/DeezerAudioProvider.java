package com.nashi.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nashi.artist.Artist;
import com.nashi.artist.AudioClip;
import com.nashi.artist.AudioSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Top tracks of a Deezer artist. A Deezer preview link is signed and expires within hours, so no URL is stored:
 * the fragment endpoint asks Deezer for a fresh one on every request, by {@code externalId}.
 */
@Component
public class DeezerAudioProvider implements AudioProvider {

    private final HttpJson http;

    public DeezerAudioProvider(HttpJson http) {
        this.http = http;
    }

    @Override
    public AudioSource source() {
        return AudioSource.DEEZER;
    }

    @Override
    public List<AudioClip> find(Artist artist) {
        Optional<String> id = ExternalIds.deezerArtistId(artist.getLinks());
        if (id.isEmpty()) {
            return List.of();
        }
        JsonObject response = http.get("https://api.deezer.com/artist/" + id.get() + "/top?limit=5");
        JsonArray data = response.has("data") && response.get("data").isJsonArray()
                ? response.getAsJsonArray("data") : new JsonArray();
        List<AudioClip> clips = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject track = element.getAsJsonObject();
            if (Json.string(track, "preview").isEmpty() || Json.string(track, "id").isEmpty()) {
                continue;
            }
            String trackId = Json.string(track, "id").get();
            clips.add(new AudioClip("dz_" + trackId, AudioSource.DEEZER, trackId, null, null,
                    Json.string(track, "title").orElse(""), 30, "Прев'ю: Deezer", Json.string(track, "link").orElse(null)));
        }
        return clips;
    }
}
