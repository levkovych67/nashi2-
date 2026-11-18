package com.nashi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.time.Clock;
import java.util.Optional;

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
}
