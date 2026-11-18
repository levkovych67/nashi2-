package com.nashi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time comes from the {@link Clock} bean so tests can fix it. Instants are UTC; {@link #KYIV} is for anything shown
 * to people or cut by calendar day.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
