package com.ecourtier.common.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "Vérification de l'état de santé du service backend")
public class HealthController {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Autowired(required = false)
    private Clock clock = Clock.system(DAKAR_ZONE);

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Vérifier le statut de l'API", description = "Retourne l'état opérationnel du service backend Spring Boot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service backend opérationnel (status UP)")
    })
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Clock effectiveClock = clock != null ? clock : Clock.system(DAKAR_ZONE);
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "E-Courtier Backend",
            "version", "1.0.0",
            "timestamp", Instant.now(effectiveClock).toString(),
            "environment", activeProfile
        );
        return ResponseEntity.ok(health);
    }
}
