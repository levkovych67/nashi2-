package com.nashi.service;

import org.springframework.web.multipart.MultipartFile;

public interface S3Service {

    /**
     * Uploads a file to S3.
     * @param file The file from the controller.
     * @param folderPath The folder inside the bucket (e.g. "artists/images").
     * @return The S3 Key (path) of the uploaded file.
     */
    String uploadFile(MultipartFile file, String folderPath);

    /**
     * Generates a full public URL for the file.
     * @param key The S3 object key.
     * @return Full URL (e.g. "https://bucket.s3.region.amazonaws.com/key").
     */
    String getFileUrl(String key);

    /**
     * Deletes a file from S3.
     * Used for cleanup jobs when an artist is REJECTED.
     * @param key The S3 object key.
     */
    void deleteFile(String key);
}