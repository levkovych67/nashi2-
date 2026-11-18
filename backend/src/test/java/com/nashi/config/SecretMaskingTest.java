package com.nashi.config;

import com.nashi.media.S3Properties;
import com.nashi.migration.V1MigrationProperties;
import com.nashi.telegram.TelegramProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Configuration records that hold secrets never print them ({@code toString} reaches logs and failure reports). */
class SecretMaskingTest {

    @Test
    void telegramPropertiesHideTheBotToken() {
        var props = new TelegramProperties("123456:secret-bot-token", true, true,
                new TelegramProperties.Moderation(-100L, 7, null));

        assertThat(props.toString()).doesNotContain("secret-bot-token").contains("botToken=***", "-100");
        assertThat(new TelegramProperties(null, false, false, null).toString()).contains("botToken=null");
    }

    @Test
    void s3PropertiesHideTheSecretKeyButKeepTheKeyId() {
        var props = new S3Properties("http://minio:9000", "eu-north-1", "bucket", "AKIAKEYID",
                "s3-secret-access-key", "https://cdn", true);

        assertThat(props.toString()).doesNotContain("s3-secret-access-key")
                .contains("secretAccessKey=***", "accessKeyId=AKIAKEYID", "bucket");
    }

    @Test
    void v1MigrationPropertiesHideTheSecretKeyAndMongoCredentials() {
        var props = new V1MigrationProperties("mongodb+srv://nashi:mongo-password@cluster0.example.net/nashi?tls=true",
                "v1-bucket", null, null, "V1KEYID", "v1-secret-access-key");

        assertThat(props.toString()).doesNotContain("v1-secret-access-key", "mongo-password", "nashi:")
                .contains("s3SecretAccessKey=***", "mongodb+srv://***@cluster0.example.net/nashi?tls=true", "v1-bucket");
        assertThat(new V1MigrationProperties("mongodb://localhost:27018/nashi", null, null, null, null, null)
                .toString()).contains("mongoUri=mongodb://localhost:27018/nashi");
    }

    @Test
    void securityPropsHideTheIpHashSecret() {
        var props = new NashiProperties.SecurityProps("ip-hash-secret-value");

        assertThat(props.toString()).doesNotContain("ip-hash-secret-value").contains("ipHashSecret=***");
    }
}
