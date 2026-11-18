package com.nashi.config;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Fills {@code @CreatedDate} and {@code @LastModifiedDate} from the {@link Clock} bean, so a test with a fixed clock
 * gets fixed timestamps. Kept out of {@code NashiApplication} so web-layer test slices start without MongoDB.
 */
@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class MongoConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    /**
     * The driver waits 30 s for a reachable server by default, so {@code GET /api/v2/health} hung that long when
     * MongoDB was down. 3 s is plenty on a healthy network; runs after the URI is applied, so it wins over a URI value.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public MongoClientSettingsBuilderCustomizer serverSelectionTimeoutCustomizer() {
        return builder -> builder.applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(3, TimeUnit.SECONDS));
    }
}
