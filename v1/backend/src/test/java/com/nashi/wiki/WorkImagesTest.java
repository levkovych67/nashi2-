package com.nashi.wiki;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkImagesTest {

    /** Thumbnail URLs as Commons imageinfo answered {@code iiurlwidth=1280} (recorded 2026-11-08). */
    private final Map<String, String> thumbs = MediaWikiParser.parseThumbUrls(Fixtures.read("imageinfo-works.json"));

    @Test
    void previewOfThumb() {
        String full = thumbs.get("Mykola Pymonenko-Brod.jpg");
        assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mykola_Pymonenko-Brod.jpg"
                + "/1280px-Mykola_Pymonenko-Brod.jpg", full);

        // 500 is a standard Wikimedia thumb width; 400 answers HTTP 400.
        assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mykola_Pymonenko-Brod.jpg"
                + "/500px-Mykola_Pymonenko-Brod.jpg", WorkImages.previewUrl(full));
    }

    @Test
    void previewOfOriginalIsSame() {
        // The file is 650 px wide, so imageinfo hands out the original instead of a 1280 px thumb.
        String original = thumbs.get("Ivan Trush - Sunbeam.jpg");
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/f/fe/Ivan_Trush_-_Sunbeam.jpg", original);

        assertEquals(original, WorkImages.previewUrl(original));
        assertNull(WorkImages.previewUrl(null));
    }
}
