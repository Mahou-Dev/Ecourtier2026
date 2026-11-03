package com.ecourtier.domain.favori.controller;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.favori.service.FavoriService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Favoris", description = "Gestion des annonces favorites de l'utilisateur connecté")
@SecurityRequirement(name = "bearerAuth")
public class FavoriController {

    private final FavoriService favoriService;

    @PostMapping("/api/v1/annonces/{id}/favoris")
    @Operation(
            summary = "Ajouter une annonce aux favoris",
            description = "Sauvegarde une annonce disponible dans la liste de favoris de l'utilisateur connecté (retourne 409 si déjà présente)."
    )
    public ResponseEntity<Void> ajouterFavori(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        favoriService.ajouterFavori(id, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/api/v1/annonces/{id}/favoris")
    @Operation(
            summary = "Retirer une annonce des favoris",
            description = "Supprime l'annonce indiquée de la liste des favoris de l'utilisateur connecté."
    )
    public ResponseEntity<Void> supprimerFavori(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        favoriService.supprimerFavori(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/utilisateurs/moi/favoris")
    @Operation(
            summary = "Liste paginée des annonces favorites de l'utilisateur connecté",
            description = "Retourne les annonces favorites de l'utilisateur connecté, triées par date d'ajout décroissante."
    )
    public ResponseEntity<Page<AnnonceResponseDTO>> listerFavoris(
            @PageableDefault(size = 12, sort = "dateAjout", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<AnnonceResponseDTO> result = favoriService.listerFavoris(authentication.getName(), pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/v1/utilisateurs/moi/favoris/count")
    @Operation(
            summary = "Nombre total de favoris de l'utilisateur connecté",
            description = "Retourne le compteur total d'annonces enregistrées en favoris par l'utilisateur connecté."
    )
    public ResponseEntity<Map<String, Long>> compterFavoris(Authentication authentication) {
        long count = favoriService.compterFavoris(authentication.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }
}
