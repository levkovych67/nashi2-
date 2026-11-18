package com.nashi.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.nashi.api.UnsupportedMediaException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = ImageProcessorTest.class.getResourceAsStream("/images/" + name)) {
            return in.readAllBytes();
        }
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static boolean isRed(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getGreen() < 80 && c.getBlue() < 80;
    }

    @Test
    void sniffsFormatsByMagicBytes() throws IOException {
        assertThat(ImageFormat.sniff(fixture("rotated-gps.jpg"))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.sniff(fixture("photo.png"))).contains(ImageFormat.PNG);
        assertThat(ImageFormat.sniff(fixture("photo.webp"))).contains(ImageFormat.WEBP);
        assertThat(ImageFormat.sniff(fixture("fake.jpg"))).isEqualTo(Optional.empty());
        assertThat(ImageFormat.sniff(new byte[0])).isEmpty();
        assertThat(ImageFormat.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }

    @Test
    void rejectsRenamedPdf() throws IOException {
        byte[] pdf = fixture("fake.jpg");
        assertThatThrownBy(() -> processor.process(pdf, "images[2]"))
                .isInstanceOfSatisfying(UnsupportedMediaException.class, e -> assertThat(e.field()).isEqualTo("images[2]"));
    }

    @Test
    void rejectsCorruptImageWithKnownMagic() {
        byte[] bad = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3};
        assertThatThrownBy(() -> processor.process(bad, "images[0]"))
                .isInstanceOfSatisfying(UnsupportedMediaException.class, e -> assertThat(e.field()).isEqualTo("images[0]"));
    }

    @Test
    void appliesOrientationAndStripsExif() throws Exception {
        byte[] input = fixture("rotated-gps.jpg");
        // the fixture really carries what the test claims to strip
        Metadata before = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
        assertThat(before.getFirstDirectoryOfType(GpsDirectory.class)).isNotNull();
        assertThat(before.getFirstDirectoryOfType(ExifIFD0Directory.class).getInteger(ExifIFD0Directory.TAG_ORIENTATION))
                .isEqualTo(6);

        ProcessedImage result = processor.process(input, "images[0]");

        assertThat(result.width()).isEqualTo(1200);
        assertThat(result.height()).isEqualTo(1600);
        BufferedImage full = decode(result.full());
        assertThat(full.getWidth()).isEqualTo(1200);
        assertThat(full.getHeight()).isEqualTo(1600);
        assertThat(isRed(full.getRGB(full.getWidth() - 30, 30))).isTrue();
        assertThat(isRed(full.getRGB(30, 30))).isFalse();

        for (byte[] out : new byte[][] {result.full(), result.thumb()}) {
            Metadata after = ImageMetadataReader.readMetadata(new ByteArrayInputStream(out));
            assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
            ExifIFD0Directory ifd0 = after.getFirstDirectoryOfType(ExifIFD0Directory.class);
            assertThat(ifd0 == null || !ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)).isTrue();
        }
    }

    @Test
    void doesNotUpscaleSmallImages() throws IOException {
        ProcessedImage result = processor.process(fixture("tiny.jpg"), "images[0]");
        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(200);
        BufferedImage full = decode(result.full());
        assertThat(full.getWidth()).isEqualTo(300);
        assertThat(full.getHeight()).isEqualTo(200);
        // thumb: centre-cropped square, scaled down only
        BufferedImage thumb = decode(result.thumb());
        assertThat(thumb.getWidth()).isEqualTo(200);
        assertThat(thumb.getHeight()).isEqualTo(200);
    }

    @Test
    void thumbIs400Square() throws IOException {
        ProcessedImage result = processor.process(fixture("rotated-gps.jpg"), "images[0]");
        BufferedImage thumb = decode(result.thumb());
        assertThat(thumb.getWidth()).isEqualTo(400);
        assertThat(thumb.getHeight()).isEqualTo(400);
        // centre crop of the portrait image: the red corner marker is cropped away
        assertThat(isRed(thumb.getRGB(370, 30))).isFalse();
    }

    @Test
    void decodesPngAndWebp() throws IOException {
        for (String name : new String[] {"photo.png", "photo.webp"}) {
            ProcessedImage result = processor.process(fixture(name), "images[0]");
            assertThat(result.width()).isEqualTo(800);
            assertThat(result.height()).isEqualTo(600);
            assertThat(ImageFormat.sniff(result.full())).contains(ImageFormat.JPEG);
            assertThat(ImageFormat.sniff(result.thumb())).contains(ImageFormat.JPEG);
        }
    }

    @Test
    void flattensTransparencyOntoWhite() throws IOException {
        ProcessedImage result = processor.process(fixture("photo.png"), "images[0]");
        BufferedImage full = decode(result.full());
        Color transparentSpot = new Color(full.getRGB(50, 50));
        assertThat(transparentSpot.getRed()).isGreaterThan(235);
        assertThat(transparentSpot.getGreen()).isGreaterThan(235);
        assertThat(transparentSpot.getBlue()).isGreaterThan(235);
        Color opaqueSpot = new Color(full.getRGB(400, 300));
        assertThat(opaqueSpot.getRed()).isGreaterThan(150);
        assertThat(opaqueSpot.getGreen()).isLessThan(110);
    }
}
