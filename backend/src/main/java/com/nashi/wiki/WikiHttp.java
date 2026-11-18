package com.nashi.wiki;

/** Fetches a Wikimedia URL and returns the response body. */
public interface WikiHttp {
    String get(String url);
}
