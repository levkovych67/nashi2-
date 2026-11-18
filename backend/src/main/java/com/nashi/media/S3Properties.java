package com.nashi.media;

import com.nashi.config.Secrets;
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

    /** The secret key is masked (the key id is not a secret): this record can end up in logs. */
    @Override
    public String toString() {
        return "S3Properties[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + ", accessKeyId="
                + accessKeyId + ", secretAccessKey=" + Secrets.mask(secretAccessKey) + ", publicBaseUrl="
                + publicBaseUrl + ", pathStyleAccess=" + pathStyleAccess + "]";
    }
}
