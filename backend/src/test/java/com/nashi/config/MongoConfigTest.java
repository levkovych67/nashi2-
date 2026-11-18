package com.nashi.config;

import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    @Test
    void serverSelectionTimeoutIsShortSoHealthAnswersPromptlyWhenMongoIsDown() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();

        new MongoConfig().serverSelectionTimeoutCustomizer().customize(builder);

        assertThat(builder.build().getClusterSettings().getServerSelectionTimeout(TimeUnit.SECONDS)).isEqualTo(3);
    }
}
