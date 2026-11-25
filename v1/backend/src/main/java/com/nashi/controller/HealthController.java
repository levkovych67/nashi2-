package com.nashi.controller;

import com.nashi.dto.HealthResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private static final String VERSION = "0.0.2-SNAPSHOT";

    @GetMapping
    public ResponseEntity<HealthResponseDTO> getHealth() {
        log.debug("Health check requested");

        Map<String, HealthResponseDTO.ComponentHealth> components = new HashMap<>();
        boolean allHealthy = true;

        // Check MongoDB
        HealthResponseDTO.ComponentHealth mongoHealth = checkMongoDB();
        components.put("mongodb", mongoHealth);
        if (!"UP".equals(mongoHealth.getStatus())) {
            allHealthy = false;
        }

        // Check System
        HealthResponseDTO.ComponentHealth systemHealth = checkSystem();
        components.put("system", systemHealth);

        HealthResponseDTO response = HealthResponseDTO.builder()
                .status(allHealthy ? "UP" : "DOWN")
                .version(VERSION)
                .timestamp(Instant.now())
                .components(components)
                .build();

        log.debug("Health check completed with status: {}", response.getStatus());

        // 503 lets the deploy script and monitors see that the API cannot serve data.
        return ResponseEntity.status(allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    private HealthResponseDTO.ComponentHealth checkMongoDB() {
        long startTime = System.currentTimeMillis();
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            long responseTime = System.currentTimeMillis() - startTime;

            return HealthResponseDTO.ComponentHealth.builder()
                    .status("UP")
                    .message("MongoDB connection successful")
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("MongoDB health check failed", e);

            // The exception text (hosts, user names) stays in the log; the public answer says only what failed.
            return HealthResponseDTO.ComponentHealth.builder()
                    .status("DOWN")
                    .message("MongoDB connection failed")
                    .responseTimeMs(responseTime)
                    .build();
        }
    }

    private HealthResponseDTO.ComponentHealth checkSystem() {
        long startTime = System.currentTimeMillis();
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            String message = String.format(
                    "Memory: %.2f%% used (%d MB / %d MB)",
                    memoryUsagePercent,
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024)
            );

            long responseTime = System.currentTimeMillis() - startTime;

            return HealthResponseDTO.ComponentHealth.builder()
                    .status("UP")
                    .message(message)
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("System health check failed", e);

            return HealthResponseDTO.ComponentHealth.builder()
                    .status("DOWN")
                    .message("System check failed")
                    .responseTimeMs(responseTime)
                    .build();
        }
    }
}
