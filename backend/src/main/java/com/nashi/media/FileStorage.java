package com.nashi.media;

/**
 * Object storage for photos. Keys are plain paths ({@code submissions/{id}/{n}.jpg}, {@code artists/{aid}/...});
 * only the {@code artists/} prefix is publicly readable, {@code submissions/} stays private.
 */
public interface FileStorage {

    void put(String key, byte[] bytes, String contentType);

    /**
     * @return the object's bytes
     * @throws software.amazon.awssdk.services.s3.model.NoSuchKeyException if the key does not exist (never returns null)
     */
    byte[] get(String key);

    /**
     * Server-side copy inside the bucket; content type is kept.
     *
     * @throws software.amazon.awssdk.services.s3.model.NoSuchKeyException if {@code fromKey} does not exist
     */
    void copy(String fromKey, String toKey);

    /** Deletes the object; deleting a missing key is not an error. */
    void delete(String key);

    /** @return the public address of the object: {@code publicBaseUrl + "/" + key} */
    String publicUrl(String key);
}
