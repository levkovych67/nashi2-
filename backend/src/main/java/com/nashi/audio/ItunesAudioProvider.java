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

/** Songs of an Apple Music artist with a 30 s preview (iTunes Lookup). Preview URLs are stable. */
@Component
public class ItunesAudioProvider implements AudioProvider {

    private final HttpJson http;

    public ItunesAudioProvider(HttpJson http) {
        this.http = http;
    }

    @Override
    public AudioSource source() {
        return AudioSource.ITUNES;
    }

    @Override
    public List<AudioClip> find(Artist artist) {
        Optional<String> id = ExternalIds.appleArtistId(artist.getLinks());
        if (id.isEmpty()) {
            return List.of();
        }
        JsonObject response = http.get("https://itunes.apple.com/lookup?id=" + id.get()
                + "&entity=song&limit=5&country=UA");
        JsonArray results = response.has("results") && response.get("results").isJsonArray()
                ? response.getAsJsonArray("results") : new JsonArray();
        List<AudioClip> clips = new ArrayList<>();
        for (JsonElement element : results) {
            JsonObject item = element.getAsJsonObject();
            Optional<String> preview = Json.string(item, "previewUrl");
            Optional<String> trackId = Json.string(item, "trackId");
            if (!"track".equals(Json.string(item, "wrapperType").orElse(null)) || preview.isEmpty() || trackId.isEmpty()) {
                continue;
            }
            clips.add(new AudioClip("it_" + trackId.get(), AudioSource.ITUNES, trackId.get(), null, preview.get(),
                    Json.string(item, "trackName").orElse(""), 30, "Прев'ю: Apple Music",
                    Json.string(item, "trackViewUrl").orElse(null)));
        }
        return clips;
    }
}
