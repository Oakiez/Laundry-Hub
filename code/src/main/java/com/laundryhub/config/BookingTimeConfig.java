package com.laundryhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BookingTimeConfig {
    @Bean
    public Clock bookingClock() {
        // Keep the same local-time convention as the existing TIMESTAMP columns.
        return Clock.systemDefaultZone();
    }
}
