package com.nashi.wiki;

/**
 * Work images come from Commons imageinfo at {@value #FULL_WIDTH} px; the preview is the same thumbnail at
 * {@value #PREVIEW_WIDTH} px. Wikimedia serves only standard thumbnail widths (2026-11-07: 330, 500, 960 and 1280
 * work, 400 answers HTTP 400), so the preview cannot be any other size.
 */
public final class WorkImages {

    static final int FULL_WIDTH = 1280;
    static final int PREVIEW_WIDTH = 500;

    private static final String FULL = "/" + FULL_WIDTH + "px-";
    private static final String PREVIEW = "/" + PREVIEW_WIDTH + "px-";

    private WorkImages() {
    }

    /**
     * The preview of a {@value #FULL_WIDTH} px thumbnail. Anything else is returned as is: a file narrower than that
     * comes as its original, which is small enough to be its own preview.
     */
    public static String previewUrl(String fullUrl) {
        if (fullUrl == null) {
            return null;
        }
        int at = fullUrl.lastIndexOf(FULL);
        return at < 0 ? fullUrl : fullUrl.substring(0, at) + PREVIEW + fullUrl.substring(at + FULL.length());
    }
}
