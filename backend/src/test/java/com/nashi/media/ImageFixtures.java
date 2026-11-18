package com.nashi.media;

import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the binaries in {@code src/test/resources/images/}; they are committed, this is only how they were made.
 * Regenerate: run {@code main} from the IDE with the working directory {@code backend/} (optional argument:
 * output directory).
 * <p>
 * {@code photo.webp} comes from {@code photo.png} with an external tool:
 * {@code cwebp -q 80 photo.png -o photo.webp} (libwebp; the JVM side decodes WebP only).
 * <ul>
 *   <li>{@code rotated-gps.jpg}: stored landscape 2000x1500, EXIF Orientation=6, GPS IFD, red 100x100 block at the
 *       stored top-left corner (after rotating it is the top-right corner of the 1500x2000 portrait).</li>
 *   <li>{@code photo.png}: 800x600 RGBA, the top-left 100x100 is fully transparent.</li>
 *   <li>{@code fake.jpg}: a PDF with a .jpg name. {@code tiny.jpg}: 300x200.</li>
 * </ul>
 */
public final class ImageFixtures {

    private ImageFixtures() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "src/test/resources/images");
        Files.createDirectories(dir);
        Files.write(dir.resolve("rotated-gps.jpg"), rotatedGps());
        Files.write(dir.resolve("tiny.jpg"), jpeg(solid(300, 200, new Color(40, 120, 200), BufferedImage.TYPE_INT_RGB)));
        Files.write(dir.resolve("photo.png"), png());
        Files.write(dir.resolve("fake.jpg"),
                "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
    }

    private static BufferedImage solid(int w, int h, Color c, int type) {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static byte[] jpeg(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] rotatedGps() throws Exception {
        BufferedImage img = solid(2000, 1500, new Color(30, 90, 200), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 0, 0));
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        TiffOutputSet set = new TiffOutputSet();
        TiffOutputDirectory root = set.getOrCreateRootDirectory();
        root.add(TiffTagConstants.TIFF_TAG_ORIENTATION, (short) 6);
        set.setGpsInDegrees(30.5234, 50.4501); // longitude, latitude
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ExifRewriter().updateExifMetadataLossless(jpeg(img), out, set);
        return out.toByteArray();
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 60, 60));
        g.fillRect(0, 0, 800, 600);
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
