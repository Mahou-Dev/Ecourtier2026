package com.ecourtier.domain.rendezvous.controller;

import com.ecourtier.domain.rendezvous.dto.RendezVousAnnulationDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousRequestDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.service.RendezVousService;
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
public class RendezVousController {

    private final RendezVousService rendezVousService;

    @PostMapping("/annonces/{id}/rendez-vous")
    @PreAuthorize("isAuthenticated()")
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
    public ResponseEntity<RendezVousResponseDTO> confirmerRendezVous(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        RendezVousResponseDTO response = rendezVousService.confirmerRendezVous(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rendez-vous/{id}/annuler")
    @PreAuthorize("@rendezVousSecurityService.isDemandeurOrOwnerOrAdmin(#id, authentication)")
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
    public ResponseEntity<RendezVousResponseDTO> marquerEffectue(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        RendezVousResponseDTO response = rendezVousService.marquerEffectue(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rendez-vous/mes-rendez-vous")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RendezVousResponseDTO>> obtenirMesRendezVous(Authentication authentication) {
        List<RendezVousResponseDTO> response = rendezVousService.obtenirMesRendezVous(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/annonces/{id}/rendez-vous")
    @PreAuthorize("@rendezVousSecurityService.isAnnonceOwnerOrAdmin(#id, authentication)")
    public ResponseEntity<List<RendezVousResponseDTO>> obtenirRendezVousAnnonce(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        List<RendezVousResponseDTO> response = rendezVousService.obtenirRendezVousAnnonce(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rendez-vous/simuler-rappels")
    @PreAuthorize("hasRole('ADMIN') or isAuthenticated()")
    public ResponseEntity<Map<String, Object>> simulerRappels() {
        int nbRappels = rendezVousService.simulerRappelsDemain();
        return ResponseEntity.ok(Map.of(
                "statut", "SUCCESS",
                "rappelsEnvoyes", nbRappels,
                "message", nbRappels + " rappel(s) SMS simulé(s) pour les RDV de demain"
        ));
    }
}
