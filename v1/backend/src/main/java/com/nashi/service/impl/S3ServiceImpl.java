package com.nashi.service.impl;

import com.nashi.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, String folderPath) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        // 1. Generate unique filename to prevent collisions (e.g. "uuid.jpg")
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String key = folderPath + "/" + UUID.randomUUID() + "." + extension;

        try {
            // 2. Prepare the request
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    // Uncomment below if you want the file to be publicly readable via ACL (not recommended for strict security)
                    // .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            // 3. Upload
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("File uploaded successfully to S3: {}", key);
            return key;

        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public String getFileUrl(String key) {
        if (key == null || key.isBlank()) return null;

        // Option A: If bucket is public, construct standard URL
        // Format: https://{bucket}.s3.{region}.amazonaws.com/{key}
        return s3Client.utilities().getUrl(GetUrlRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()).toExternalForm();

        // Option B (Secure): If bucket is private, generate Presigned URL here
    }

    @Override
    public void deleteFile(String key) {
        if (key == null || key.isBlank()) return;

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("File deleted from S3: {}", key);
        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", key, e);
            // We usually don't throw exception here to avoid stopping the whole cleanup job
        }
    }
}
