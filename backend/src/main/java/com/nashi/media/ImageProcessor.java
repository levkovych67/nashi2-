package com.nashi.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.nashi.api.UnsupportedMediaException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded JPEG/PNG/WebP into a display JPEG and a thumbnail. The pixels are decoded, rotated by the EXIF
 * Orientation and re-encoded from a {@link BufferedImage} with no metadata, so EXIF, GPS and XMP never reach storage.
 */
@Component
public class ImageProcessor {

    static final int MAX_SIDE = 1600;
    static final int THUMB_SIDE = 400;
    static final float JPEG_QUALITY = 0.85f;
    /** Refuses decompression bombs: a small file can declare a huge canvas. */
    private static final long MAX_PIXELS = 100_000_000L;

    /**
     * @param field name of the upload for the error ({@code images[2]})
     * @throws UnsupportedMediaException for an unknown format or a file that cannot be decoded
     */
    public ProcessedImage process(byte[] input, String field) {
        if (ImageFormat.sniff(input).isEmpty()) {
            throw new UnsupportedMediaException(field);
        }
        BufferedImage source;
        try {
            source = decode(input);
        } catch (IOException | RuntimeException e) {
            throw new UnsupportedMediaException(field);
        }
        if (source == null) {
            throw new UnsupportedMediaException(field);
        }
        try {
            BufferedImage oriented = applyOrientation(flattenToRgb(source), orientationOf(input));
            BufferedImage full = fitWithin(oriented, MAX_SIDE);
            return new ProcessedImage(encodeJpeg(full), encodeJpeg(thumbnail(full)), full.getWidth(), full.getHeight());
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        }
    }

    private static BufferedImage decode(byte[] input) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** EXIF Orientation 1-8; 1 when absent or unreadable. */
    private static int orientationOf(byte[] input) {
        try {
            var directory = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input))
                    .getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // no readable metadata: show the pixels as stored
        }
        return 1;
    }

    /** Opaque RGB; transparent areas become white (JPEG has no alpha). */
    private static BufferedImage flattenToRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage applyOrientation(BufferedImage img, int orientation) {
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t;
        boolean swap = false;
        switch (orientation) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> { t = new AffineTransform(0, 1, 1, 0, 0, 0); swap = true; }
            case 6 -> { t = new AffineTransform(0, 1, -1, 0, h, 0); swap = true; }
            case 7 -> { t = new AffineTransform(0, -1, -1, 0, h, w); swap = true; }
            case 8 -> { t = new AffineTransform(0, -1, 1, 0, 0, w); swap = true; }
            default -> { return img; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    /** Scales down so the longer side is at most {@code maxSide}; never scales up. */
    private static BufferedImage fitWithin(BufferedImage img, int maxSide) throws IOException {
        if (Math.max(img.getWidth(), img.getHeight()) <= maxSide) {
            return img;
        }
        return Thumbnails.of(img).size(maxSide, maxSide).asBufferedImage();
    }

    /** Centre-cropped square, at most {@value #THUMB_SIDE} per side. */
    private static BufferedImage thumbnail(BufferedImage img) throws IOException {
        int side = Math.min(img.getWidth(), img.getHeight());
        BufferedImage square = img.getSubimage((img.getWidth() - side) / 2, (img.getHeight() - side) / 2, side, side);
        return fitWithin(square, THUMB_SIDE);
    }

    private static byte[] encodeJpeg(BufferedImage img) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
