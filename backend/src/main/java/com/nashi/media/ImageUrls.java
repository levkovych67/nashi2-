package com.nashi.media;

import com.nashi.artist.ImageAsset;

/** Public URLs of stored images, shared by the list and map DTOs. */
public final class ImageUrls {

    private ImageUrls() {
    }

    /** The stored thumbnail wins over an external one; {@code null} when the image has neither. */
    public static String thumb(ImageAsset image, FileStorage storage) {
        return image.thumbKey() != null ? storage.publicUrl(image.thumbKey()) : image.thumbUrl();
    }
}
