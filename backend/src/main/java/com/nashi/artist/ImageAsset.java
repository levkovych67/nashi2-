package com.nashi.artist;

/** {@code key}/{@code thumbKey} are S3 keys of our uploads; {@code url}/{@code thumbUrl} are external (Commons). */
public record ImageAsset(String key, String url, String thumbKey, String thumbUrl, String alt, ImageCredit credit) {
}
