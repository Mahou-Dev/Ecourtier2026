package com.ecourtier.domain.avis.controller;

import com.ecourtier.domain.avis.dto.ReputationResponseDTO;
import com.ecourtier.domain.avis.service.AvisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Endpoints publics et réputation des utilisateurs")
public class UtilisateurController {

    private final AvisService avisService;

    @GetMapping("/{id}/reputation")
    @Operation(summary = "Obtenir la réputation d'un propriétaire", description = "Moyenne des notes et nombre total d'avis calculés sur l'ensemble de ses annonces.")
    public ResponseEntity<ReputationResponseDTO> obtenirReputationUtilisateur(@PathVariable UUID id) {
        ReputationResponseDTO response = avisService.obtenirReputationUtilisateur(id);
        return ResponseEntity.ok(response);
    }
}
