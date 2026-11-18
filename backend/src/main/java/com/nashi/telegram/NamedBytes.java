package com.nashi.telegram;

/** A photo to upload: its file name and content. */
public record NamedBytes(String filename, byte[] bytes) {
}
