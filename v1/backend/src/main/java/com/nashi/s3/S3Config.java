package com.nashi.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    /**
     * Create the real AWS S3 client only when credentials are configured.
     *
     * This keeps local tests (and local dev without AWS creds) from failing at startup.
     */
    @Bean
    @ConditionalOnProperty(prefix = "aws.s3", name = {"access-key-id", "secret-access-key"})
    public S3Client s3Client(S3Properties properties) {

        var credentials = AwsBasicCredentials.create(
                properties.accessKeyId(),
                properties.secretAccessKey()
        );

        var region = Region.of(properties.region());

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(region)
                .build();
    }
}
