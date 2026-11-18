package com.nashi.wiki;

import com.nashi.media.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Copies the Commons thumbnail of an imported portrait into our storage, so map pins carry our URL, which compresses
 * far better than a Commons URL ({@code docs/adr/0003-map-pins-payload.md}). The download goes through the polite
 * Wikimedia client (project User-Agent, the request gap); upload.wikimedia.org is Wikimedia too. Only JPEG, PNG and
 * WebP are stored: an SVG original could carry script into our public bucket.
 */
@Component
public class CommonsThumbnails {

    private static final Logger log = LoggerFactory.getLogger(CommonsThumbnails.class);
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final WikiFileHttp http;
    private final FileStorage storage;

    public CommonsThumbnails(WikiFileHttp http, FileStorage storage) {
        this.http = http;
        this.storage = storage;
    }

    /** {@code artists/{artistId}/0-thumb.jpg}, next to the photos of the form. */
    public static String key(String artistId) {
        return "artists/" + artistId + "/0-thumb.jpg";
    }

    /**
     * Downloads the thumbnail and stores it under {@link #key}.
     *
     * @return the key; null when the thumbnail could not be downloaded or stored (logged), so the caller keeps the
     *         external URL
     */
    public String store(String thumbUrl, String artistId) {
        try {
            WikiFileHttp.WikiFile file = http.getFile(thumbUrl);
            if (file.bytes() == null || file.bytes().length == 0 || !IMAGE_TYPES.contains(file.contentType())) {
                log.warn("[WIKI] thumbnail {} of artist {} is not a photo ({}); the Commons URL stays", thumbUrl,
                        artistId, file.contentType());
                return null;
            }
            String key = key(artistId);
            storage.put(key, file.bytes(), file.contentType());
            return key;
        } catch (RuntimeException e) {
            log.warn("[WIKI] could not copy thumbnail {} of artist {}; the Commons URL stays: {}", thumbUrl, artistId,
                    e.getMessage());
            return null;
        }
    }

    /** Removes a thumbnail {@link #store} put for an artist that was not saved; best effort. */
    public void delete(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("[WIKI] could not delete {} of an artist that was not saved: {}", key, e.getMessage());
        }
    }
}
