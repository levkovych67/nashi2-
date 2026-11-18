package com.nashi.migration;

import com.nashi.config.Secrets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the V1 data is read from ({@code nashi.migration.v1.*}). Unset {@code mongoUri} means the migration is not
 * configured and {@code /migrate_v1} says so. The values come from the secrets file, never from the repository.
 *
 * @param mongoUri        the V1 database, as {@code mongodb://host/nashi}; its database name is the V1 one
 * @param s3Bucket        the V1 bucket, read only
 * @param s3Region        defaults to {@code eu-north-1}
 * @param s3Endpoint      empty means AWS; otherwise an S3-compatible server (MinIO locally)
 */
@ConfigurationProperties("nashi.migration.v1")
public record V1MigrationProperties(String mongoUri, String s3Bucket, String s3Region, String s3Endpoint,
                                    String s3AccessKeyId, String s3SecretAccessKey) {

    static final String DEFAULT_REGION = "eu-north-1";

    public V1MigrationProperties {
        if (s3Region == null || s3Region.isBlank()) {
            s3Region = DEFAULT_REGION;
        }
    }

    public boolean configured() {
        return mongoUri != null && !mongoUri.isBlank();
    }

    /** The secret key and the credentials in {@code mongoUri} are masked: this record can end up in logs. */
    @Override
    public String toString() {
        return "V1MigrationProperties[mongoUri=" + Secrets.maskUriCredentials(mongoUri) + ", s3Bucket=" + s3Bucket
                + ", s3Region=" + s3Region + ", s3Endpoint=" + s3Endpoint + ", s3AccessKeyId=" + s3AccessKeyId
                + ", s3SecretAccessKey=" + Secrets.mask(s3SecretAccessKey) + "]";
    }
}
