package com.nashi.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    private static long selectionMillis(String uri, Duration property) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        // Boot applies the connection string first, then the customizers
        if (uri != null) {
            builder.applyConnectionString(new ConnectionString(uri));
        }
        new MongoConfig().serverSelectionTimeoutCustomizer(property).customize(builder);
        return builder.build().getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS);
    }

    @Test
    void appliesTheConfiguredTimeout() {
        assertThat(selectionMillis(null, Duration.ofSeconds(3))).isEqualTo(3000);
    }

    @Test
    void propertyWinsOverServerSelectionTimeoutInTheUri() {
        assertThat(selectionMillis("mongodb://localhost/x?serverSelectionTimeoutMS=7000", Duration.ofSeconds(3)))
                .isEqualTo(3000);
    }
}
