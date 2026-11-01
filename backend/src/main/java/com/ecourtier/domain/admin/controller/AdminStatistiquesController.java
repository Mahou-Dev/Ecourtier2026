package com.ecourtier.domain.admin.controller;

import com.ecourtier.domain.admin.dto.AdminStatistiquesDTO;
import com.ecourtier.domain.admin.service.AdminStatistiquesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistiques")
@RequiredArgsConstructor
@Tag(name = "Administration Tableau de Bord", description = "Endpoint de métriques globales et de pilotage réservé à l'ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatistiquesController {

    private final AdminStatistiquesService adminStatistiquesService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir toutes les métriques de la plateforme", description = "Endpoint unique agrégeant les métriques d'utilisateurs, d'annonces, d'activité commerciale, de modération et de l'assistant IA pour le tableau de bord administrateur.")
    public ResponseEntity<AdminStatistiquesDTO> obtenirStatistiques(
            @RequestParam(name = "periode", required = false, defaultValue = "30j") String periode
    ) {
        AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques(periode);
        return ResponseEntity.ok(stats);
    }
}
