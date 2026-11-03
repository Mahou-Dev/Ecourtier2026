package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.domain.utilisateur.dto.ChangerMotDePasseRequestDTO;
import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.service.UtilisateurManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/utilisateurs/moi")
@RequiredArgsConstructor
@Tag(name = "Mon Compte Utilisateur", description = "Consultation du profil connecté et changement de mot de passe")
@SecurityRequirement(name = "bearerAuth")
public class UtilisateurCompteController {

    private final UtilisateurManagementService utilisateurManagementService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consulter le profil de l'utilisateur connecté",
            description = "Retourne les informations du compte actuellement authentifié."
    )
    public ResponseEntity<UtilisateurResponseDTO> obtenirMonProfil(Authentication authentication) {
        return ResponseEntity.ok(utilisateurManagementService.obtenirProfilCourant(authentication.getName()));
    }

    @PutMapping("/mot-de-passe")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Modifier le mot de passe de l'utilisateur connecté",
            description = "Vérifie l'ancien mot de passe et enregistre le nouveau mot de passe conforme à la politique de sécurité (8 caractères minimum, une majuscule et un chiffre)."
    )
    public ResponseEntity<Map<String, String>> changerMotDePasse(
            @Valid @RequestBody ChangerMotDePasseRequestDTO request,
            Authentication authentication
    ) {
        utilisateurManagementService.changerMotDePasse(authentication.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }
}
