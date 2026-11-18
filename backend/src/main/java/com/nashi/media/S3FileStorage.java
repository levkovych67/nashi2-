package com.nashi.media;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/** {@link FileStorage} on AWS S3 or an S3-compatible server (MinIO). The bucket must already exist. */
@Component
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    @Autowired
    public S3FileStorage(S3Properties props) {
        this(client(props.endpoint(), props.region(), props.accessKeyId(), props.secretAccessKey(),
                props.pathStyleAccess()), props.bucket(), props.publicBaseUrl());
    }

    private S3FileStorage(S3Client s3, String bucket, String publicBaseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * Storage on another bucket than the application's own, for reading the V1 files in the migration. Not a bean:
     * the caller closes it. Path-style addressing is used when an endpoint is given (MinIO). It has no public address.
     */
    public static S3FileStorage forBucket(String endpoint, String region, String bucket, String accessKeyId,
                                          String secretAccessKey) {
        boolean custom = endpoint != null && !endpoint.isBlank();
        return new S3FileStorage(client(endpoint, region, accessKeyId, secretAccessKey, custom), bucket, null);
    }

    private static S3Client client(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                   boolean pathStyle) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .forcePathStyle(pathStyle);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void copy(String fromKey, String toKey) {
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(fromKey)
                .destinationBucket(bucket).destinationKey(toKey)
                .build());
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @PreDestroy
    public void close() {
        s3.close();
    }
}
