package com.nashi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs unless {@code nashi.scheduling.enabled=false} (the test profile). */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "nashi.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
