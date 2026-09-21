package com.ecourtier.common.controller;

import com.ecourtier.common.service.DemoDataSeederService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administration Seeding", description = "Endpoints d'initialisation des données de démo réservés à l'ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminSeedController {

    private final DemoDataSeederService demoDataSeederService;

    @PostMapping("/seed-demo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Initialiser les données de démonstration en production", description = "Déclenche le peuplement manuel de la base avec des annonces et comptes de démo. Endpoint idempotent : ignoré si la base contient déjà des annonces.")
    public ResponseEntity<Map<String, Object>> seedDemoData() {
        boolean seeded = demoDataSeederService.seedDemoData();
        if (seeded) {
            return ResponseEntity.ok(Map.of(
                    "message", "Données de démonstration initialisées avec succès.",
                    "seeded", true
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "message", "La base de données contient déjà des annonces. Aucune donnée n'a été insérée.",
                    "seeded", false
            ));
        }
    }
}
