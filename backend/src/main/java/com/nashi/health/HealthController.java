package com.nashi.health;

import io.swagger.v3.oas.annotations.Operation;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v2/health} ({@code docs/02-contracts.md} 2.12): {@code 200 {"status":"UP"}} when MongoDB answers
 * {@code ping}, otherwise {@code 503 {"status":"DOWN"}} without the exception text.
 */
@RestController
@RequestMapping("/api/v2")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final MongoTemplate mongo;

    public HealthController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Operation(operationId = "getHealth", summary = "Стан сервісу")
    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        boolean up;
        try {
            up = isOk(mongo.executeCommand("{ ping: 1 }"));
            if (!up) {
                log.warn("Health check: MongoDB ping did not return ok");
            }
        } catch (RuntimeException e) {
            log.warn("Health check: MongoDB ping failed: {}", e.toString());
            up = false;
        }
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(new Health(up ? Status.UP : Status.DOWN));
    }

    private static boolean isOk(Document reply) {
        return reply != null && reply.get("ok") instanceof Number ok && ok.doubleValue() == 1.0;
    }

    public enum Status { UP, DOWN }

    public record Health(Status status) {
    }
}
