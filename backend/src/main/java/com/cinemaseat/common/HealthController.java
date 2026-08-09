package com.cinemaseat.common;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * Health endpoints (addendum A8).
 *  - /health       : liveness, never touches DB or gateway.
 *  - /health/ready : readiness, lightweight DB pool probe. Still does not touch gateway.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping(path = "/health/ready", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ready() {
        try (Connection c = dataSource.getConnection()) {
            boolean valid = c.isValid(1);
            if (valid) {
                return ResponseEntity.ok(Map.of("status", "UP", "db", "UP"));
            }
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "db", "DOWN"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "db", "DOWN"));
        }
    }
}