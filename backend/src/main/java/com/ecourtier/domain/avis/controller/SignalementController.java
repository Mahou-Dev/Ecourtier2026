package com.ecourtier.domain.avis.controller;

import com.ecourtier.domain.avis.dto.SignalementCreateDTO;
import com.ecourtier.domain.avis.dto.SignalementResponseDTO;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.service.SignalementService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Signalements d'Arnaques", description = "Signalement d'annonces frauduleuses et modération admin")
public class SignalementController {

    private final SignalementService signalementService;

    @PostMapping("/api/v1/annonces/{id}/signalements")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Signaler une annonce", description = "Signaler une annonce suspecte ou frauduleuse (accessible à tout utilisateur connecté).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Signalement enregistré"),
            @ApiResponse(responseCode = "400", description = "Motif invalide ou auto-signalement"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "404", description = "Annonce introuvable"),
            @ApiResponse(responseCode = "409", description = "Annonce déjà signalée par cet utilisateur")
    })
    public ResponseEntity<SignalementResponseDTO> creerSignalement(
            @PathVariable UUID id,
            @Valid @RequestBody SignalementCreateDTO request,
            Authentication authentication
    ) {
        SignalementResponseDTO response = signalementService.creerSignalement(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/admin/signalements")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lister les signalements", description = "Accès administrateur avec filtrage optionnel par statut.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste paginée des signalements"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur")
    })
    public ResponseEntity<Page<SignalementResponseDTO>> listerSignalements(
            @RequestParam(required = false) StatutSignalement statut,
            @ParameterObject @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SignalementResponseDTO> result = signalementService.listerSignalements(statut, pageable);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/api/v1/admin/signalements/{id}/traiter")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Traiter un signalement", description = "Modérer un signalement et éventuellement suspendre l'annonce associée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signalement modéré avec succès"),
            @ApiResponse(responseCode = "400", description = "Statut de traitement invalide"),
            @ApiResponse(responseCode = "401", description = "Authentification JWT requise"),
            @ApiResponse(responseCode = "403", description = "Accès strictement réservé à l'administrateur"),
            @ApiResponse(responseCode = "404", description = "Signalement introuvable"),
            @ApiResponse(responseCode = "409", description = "Suspension impossible (annonce supprimée ou transaction BLOQUE active)")
    })
    public ResponseEntity<SignalementResponseDTO> traiterSignalement(
            @PathVariable UUID id,
            @Valid @RequestBody TraiterSignalementDTO request
    ) {
        SignalementResponseDTO response = signalementService.traiterSignalement(id, request);
        return ResponseEntity.ok(response);
    }
}
