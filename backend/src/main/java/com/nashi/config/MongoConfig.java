package com.nashi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
     * How long the driver waits for a reachable server ({@code nashi.mongo.server-selection-timeout}). Production keeps
     * the driver default of 30 s so a replica-set election or failover does not fail requests; the local and test
     * profiles set 3 s so {@code GET /api/v2/health} answers promptly when MongoDB is down. Boot applies customizers
     * on top of the settings built from the connection string, so this property wins over a
     * {@code serverSelectionTimeoutMS} in the URI.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public MongoClientSettingsBuilderCustomizer serverSelectionTimeoutCustomizer(
            @Value("${nashi.mongo.server-selection-timeout:30s}") Duration timeout) {
        return builder -> builder.applyToClusterSettings(
                cluster -> cluster.serverSelectionTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS));
    }
}
