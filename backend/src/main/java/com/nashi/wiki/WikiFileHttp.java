package com.nashi.wiki;

/** Downloads a Wikimedia file, such as a Commons thumbnail on upload.wikimedia.org, as bytes. */
@FunctionalInterface
public interface WikiFileHttp {

    WikiFile getFile(String url);

    /** @param contentType the media type of the response without parameters; null when the server sent none */
    record WikiFile(byte[] bytes, String contentType) {
    }
}
