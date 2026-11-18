package com.nashi.media;

import java.util.Optional;

/** Accepted upload formats, recognised by magic bytes (never by file name or declared content type). */
public enum ImageFormat {
    JPEG, PNG, WEBP;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** @param head the first bytes of the file (at least 12 are needed for WebP) */
    public static Optional<ImageFormat> sniff(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (head.length >= PNG_MAGIC.length && startsWith(head, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (head.length >= 12 && startsWith(head, 0, new byte[] {'R', 'I', 'F', 'F'})
                && startsWith(head, 8, new byte[] {'W', 'E', 'B', 'P'})) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
