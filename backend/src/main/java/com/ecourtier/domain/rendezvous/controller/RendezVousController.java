package com.ecourtier.domain.rendezvous.controller;

import com.ecourtier.domain.rendezvous.dto.RendezVousAnnulationDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousRequestDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.service.RendezVousService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Rendez-vous", description = "Gestion des visites, confirmation automatique/manuelle et rappels SMS simulés")
@SecurityRequirement(name = "bearerAuth")
public class RendezVousController {

    private final RendezVousService rendezVousService;

    @PostMapping("/annonces/{id}/rendez-vous")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Demander un rendez-vous de visite",
            description = "Crée une demande de visite. Si confirmationAutomatique=true sur l'annonce et qu'aucun RDV CONFIRME n'existe à ±60 min, le RDV passe directement à CONFIRME."
    )
    public ResponseEntity<RendezVousResponseDTO> demanderRendezVous(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RendezVousRequestDTO request,
            Authentication authentication
    ) {
        RendezVousResponseDTO response = rendezVousService.demanderRendezVous(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/rendez-vous/{id}/confirmer")
    @PreAuthorize("@rendezVousSecurityService.isOwnerOrAdmin(#id, authentication)")
    @Operation(summary = "Confirmer manuellement un rendez-vous de visite", description = "Réservé au propriétaire/agent de l'annonce ou à un ADMIN. Vérifie l'absence de conflit à ±60 min.")
    public ResponseEntity<RendezVousResponseDTO> confirmerRendezVous(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        RendezVousResponseDTO response = rendezVousService.confirmerRendezVous(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rendez-vous/{id}/annuler")
    @PreAuthorize("@rendezVousSecurityService.isDemandeurOrOwnerOrAdmin(#id, authentication)")
    @Operation(summary = "Annuler un rendez-vous de visite", description = "Annule le RDV et envoie un SMS simulé à l'autre partie.")
    public ResponseEntity<RendezVousResponseDTO> annulerRendezVous(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) RendezVousAnnulationDTO request,
            Authentication authentication
    ) {
        RendezVousResponseDTO response = rendezVousService.annulerRendezVous(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rendez-vous/{id}/marquer-effectue")
    @PreAuthorize("@rendezVousSecurityService.isOwnerOrAdmin(#id, authentication)")
    @Operation(summary = "Marquer une visite comme effectuée", description = "Débloque l'accès à l'initiation d'une transaction et au dépôt d'avis.")
    public ResponseEntity<RendezVousResponseDTO> marquerEffectue(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        RendezVousResponseDTO response = rendezVousService.marquerEffectue(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rendez-vous/mes-rendez-vous")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lister mes rendez-vous (demandeur ou propriétaire)")
    public ResponseEntity<List<RendezVousResponseDTO>> obtenirMesRendezVous(Authentication authentication) {
        List<RendezVousResponseDTO> response = rendezVousService.obtenirMesRendezVous(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/annonces/{id}/rendez-vous")
    @PreAuthorize("@rendezVousSecurityService.isAnnonceOwnerOrAdmin(#id, authentication)")
    @Operation(summary = "Lister l'agenda des rendez-vous d'une annonce")
    public ResponseEntity<List<RendezVousResponseDTO>> obtenirRendezVousAnnonce(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        List<RendezVousResponseDTO> response = rendezVousService.obtenirRendezVousAnnonce(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rendez-vous/simuler-rappels")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Déclencher manuellement les rappels SMS de demain (démonstration ADMIN)")
    public ResponseEntity<Map<String, Object>> simulerRappels() {
        int nbRappels = rendezVousService.simulerRappelsDemain();
        return ResponseEntity.ok(Map.of(
                "statut", "SUCCESS",
                "rappelsEnvoyes", nbRappels,
                "message", nbRappels + " rappel(s) SMS simulé(s) pour les RDV de demain"
        ));
    }
}
