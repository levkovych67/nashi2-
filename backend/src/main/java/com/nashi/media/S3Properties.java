package com.nashi.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Storage settings under {@code nashi.s3.*}.
 *
 * @param endpoint        empty means AWS; otherwise the S3-compatible endpoint (MinIO locally)
 * @param publicBaseUrl   CDN or public bucket address without a trailing slash; {@code publicUrl(key)} appends
 *                        {@code "/" + key}
 * @param pathStyleAccess {@code nashi.s3.path-style-access}; false (AWS virtual-hosted style) unless set, MinIO needs true
 */
@ConfigurationProperties("nashi.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKeyId,
        String secretAccessKey,
        String publicBaseUrl,
        @DefaultValue("false") boolean pathStyleAccess) {
}
