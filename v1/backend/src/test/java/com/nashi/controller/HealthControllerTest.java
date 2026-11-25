package com.nashi.controller;

import com.nashi.dto.HealthResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final HealthController controller = new HealthController(mongoTemplate);

    @Test
    void mongoDownGives503WithoutExceptionText() {
        when(mongoTemplate.executeCommand(anyString()))
                .thenThrow(new IllegalStateException("Timed out connecting to db-7.internal:27017 as nashi_admin"));

        ResponseEntity<HealthResponseDTO> response = controller.getHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().getStatus());
        HealthResponseDTO.ComponentHealth mongo = response.getBody().getComponents().get("mongodb");
        assertEquals("DOWN", mongo.getStatus());
        assertEquals("MongoDB connection failed", mongo.getMessage());
        assertFalse(response.getBody().toString().contains("db-7.internal"));
        assertFalse(response.getBody().toString().contains("nashi_admin"));
    }

    @Test
    void mongoUpGives200() {
        ResponseEntity<HealthResponseDTO> response = controller.getHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().getStatus());
    }
}
