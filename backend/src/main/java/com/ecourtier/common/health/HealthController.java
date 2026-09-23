package com.ecourtier.common.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "Vérification de l'état de santé du service backend")
public class HealthController {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping
    @Operation(summary = "Vérifier le statut de l'API", description = "Retourne l'état opérationnel du service backend Spring Boot.")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "E-Courtier Backend",
            "version", "1.0.0",
            "timestamp", Instant.now().toString(),
            "environment", activeProfile
        );
        return ResponseEntity.ok(health);
    }
}
