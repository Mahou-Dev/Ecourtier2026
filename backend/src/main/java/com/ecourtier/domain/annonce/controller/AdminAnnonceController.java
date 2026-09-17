package com.ecourtier.domain.annonce.controller;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.dto.VerificationDecisionDTO;
import com.ecourtier.domain.annonce.service.AnnonceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<Page<AnnonceResponseDTO>> listerAnnoncesEnAttenteVerification(
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<AnnonceResponseDTO> response = annonceService.listerAnnoncesEnAttenteVerification(pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/verifier")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Valider ou rejeter la vérification d'une annonce", description = "Attribution du badge 'Annonce Vérifiée' (VERIFIEE) ou rejet avec motif (REJETEE). Réservé à l'ADMIN.")
    public ResponseEntity<AnnonceResponseDTO> deciderVerificationAnnonce(
            @PathVariable UUID id,
            @Valid @RequestBody VerificationDecisionDTO decision
    ) {
        AnnonceResponseDTO response = annonceService.deciderVerificationAnnonce(id, decision);
        return ResponseEntity.ok(response);
    }
}
