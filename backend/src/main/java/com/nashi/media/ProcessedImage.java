package com.nashi.media;

/**
 * Result of {@link ImageProcessor#process}: two JPEGs without metadata.
 *
 * @param full   longer side at most 1600, orientation applied
 * @param thumb  centre-cropped square, at most 400x400
 * @param width  width of {@code full} in pixels
 * @param height height of {@code full} in pixels
 */
public record ProcessedImage(byte[] full, byte[] thumb, int width, int height) {
}
