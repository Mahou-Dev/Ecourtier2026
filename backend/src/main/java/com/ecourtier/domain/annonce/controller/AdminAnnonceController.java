package com.ecourtier.domain.annonce.controller;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.dto.AnnonceStatutUpdateDTO;
import com.ecourtier.domain.annonce.dto.VerificationDecisionDTO;
import com.ecourtier.domain.annonce.service.AnnonceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/annonces")
@RequiredArgsConstructor
@Tag(name = "Administration Annonces & Vérifications", description = "Endpoints de modération et d'attribution du badge Annonce Vérifiée réservés à l'ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminAnnonceController {

    private final AnnonceService annonceService;

    @GetMapping("/en-attente-verification")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les annonces en attente de vérification", description = "Réservé aux administrateurs E-Courtier pour l'audit des pièces justificatives.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page des annonces en attente de vérification"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur")
    })
    public ResponseEntity<Page<AnnonceResponseDTO>> listerAnnoncesEnAttenteVerification(
            @ParameterObject @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<AnnonceResponseDTO> response = annonceService.listerAnnoncesEnAttenteVerification(pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/verifier")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Valider ou rejeter la vérification d'une annonce", description = "Attribution du badge 'Annonce Vérifiée' (VERIFIEE) ou rejet avec motif (REJETEE). Réservé à l'ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Décision de vérification enregistrée"),
            @ApiResponse(responseCode = "400", description = "Pièces justificatives manquantes ou motif de rejet absent"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur"),
            @ApiResponse(responseCode = "404", description = "Annonce introuvable")
    })
    public ResponseEntity<AnnonceResponseDTO> deciderVerificationAnnonce(
            @PathVariable UUID id,
            @Valid @RequestBody VerificationDecisionDTO decision
    ) {
        AnnonceResponseDTO response = annonceService.deciderVerificationAnnonce(id, decision);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier administrativement le statut de publication d'une annonce", description = "Permet uniquement à un administrateur de suspendre une annonce disponible (SUSPENDUE) ou de lever une suspension administrative (DISPONIBLE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut de publication mis à jour"),
            @ApiResponse(responseCode = "400", description = "Transition ou payload invalide"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur"),
            @ApiResponse(responseCode = "404", description = "Annonce introuvable"),
            @ApiResponse(responseCode = "409", description = "Suspension impossible (transaction BLOQUE active ou état incompatible)")
    })
    public ResponseEntity<AnnonceResponseDTO> changerStatutPublication(
            @PathVariable UUID id,
            @Valid @RequestBody AnnonceStatutUpdateDTO request,
            Authentication authentication
    ) {
        String adminEmail = authentication != null ? authentication.getName() : "ADMIN";
        AnnonceResponseDTO response = annonceService.changerStatutPublicationAdmin(id, request, adminEmail);
        return ResponseEntity.ok(response);
    }
}
