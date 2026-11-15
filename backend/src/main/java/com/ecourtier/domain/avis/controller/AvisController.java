package com.ecourtier.domain.avis.controller;

import com.ecourtier.domain.avis.dto.AvisCreateDTO;
import com.ecourtier.domain.avis.dto.AvisResponseDTO;
import com.ecourtier.domain.avis.dto.AvisSummaryDTO;
import com.ecourtier.domain.avis.service.AvisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/annonces")
@RequiredArgsConstructor
@Tag(name = "Avis & Evaluation", description = "Gestion des avis sur les annonces immobilières")
public class AvisController {

    private final AvisService avisService;

    @PostMapping("/{id}/avis")
    @PreAuthorize("hasRole('LOCATAIRE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Déposer un avis sur une annonce", description = "Réservé aux locataires ayant préalablement effectué une visite validée (statut EFFECTUE).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avis enregistré avec succès"),
            @ApiResponse(responseCode = "400", description = "Note/commentaire invalide ou visite EFFECTUE manquante"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès réservé aux locataires"),
            @ApiResponse(responseCode = "404", description = "Annonce introuvable"),
            @ApiResponse(responseCode = "409", description = "Avis déjà déposé pour cette annonce par ce locataire")
    })
    public ResponseEntity<AvisResponseDTO> creerAvis(
            @PathVariable UUID id,
            @Valid @RequestBody AvisCreateDTO request,
            Authentication authentication
    ) {
        AvisResponseDTO response = avisService.creerAvis(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/avis")
    @SecurityRequirements
    @Operation(summary = "Obtenir la liste et la moyenne des avis d'une annonce", description = "Accès public.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste et synthèse des avis de l'annonce"),
            @ApiResponse(responseCode = "404", description = "Annonce introuvable")
    })
    public ResponseEntity<AvisSummaryDTO> obtenirAvisAnnonce(@PathVariable UUID id) {
        AvisSummaryDTO summary = avisService.obtenirAvisAnnonce(id);
        return ResponseEntity.ok(summary);
    }
}
