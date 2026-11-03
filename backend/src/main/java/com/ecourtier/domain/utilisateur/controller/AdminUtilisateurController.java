package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.domain.utilisateur.dto.AdminCreateUtilisateurRequestDTO;
import com.ecourtier.domain.utilisateur.dto.AdminCreateUtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.dto.ChangeRoleRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangeStatutRequestDTO;
import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.service.UtilisateurManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Administration Utilisateurs", description = "Gestion des comptes utilisateurs, rôles (dont AGENT) et activation/désactivation réservée aux administrateurs")
@SecurityRequirement(name = "bearerAuth")
public class AdminUtilisateurController {

    private final UtilisateurManagementService utilisateurManagementService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Lister les utilisateurs avec pagination et filtres (ADMIN)",
            description = "Retourne la liste paginée des comptes utilisateurs avec filtrage optionnel par rôle, statut actif/inactif et recherche textuelle (nom, prénom, email, téléphone)."
    )
    public ResponseEntity<Page<UtilisateurResponseDTO>> listerTousLesUtilisateurs(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) String recherche,
            @PageableDefault(size = 20, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(utilisateurManagementService.listerUtilisateurs(role, actif, recherche, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Créer un compte utilisateur avec mot de passe temporaire (ADMIN)",
            description = "Permet à un administrateur de créer un compte (AGENT, PROPRIETAIRE, LOCATAIRE ou ADMIN) avec un mot de passe temporaire conforme à la politique de sécurité, retourné une seule fois."
    )
    public ResponseEntity<AdminCreateUtilisateurResponseDTO> creerUtilisateur(
            @Valid @RequestBody AdminCreateUtilisateurRequestDTO request
    ) {
        AdminCreateUtilisateurResponseDTO response = utilisateurManagementService.creerUtilisateurParAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Modifier le rôle d'un utilisateur (ADMIN)",
            description = "Change le rôle d'un compte utilisateur. Empêche un administrateur de se retirer son propre rôle ADMIN et garantit le maintien d'au moins un ADMIN actif."
    )
    public ResponseEntity<UtilisateurResponseDTO> changerRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(utilisateurManagementService.changerRole(id, request.getRole(), authentication.getName()));
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Activer ou désactiver un compte utilisateur (ADMIN)",
            description = "Active ou suspend immédiatement un compte utilisateur (y compris le rejet immédiat des jetons JWT actifs et du refresh). Empêche l'auto-désactivation et protège le dernier ADMIN actif."
    )
    public ResponseEntity<UtilisateurResponseDTO> changerStatut(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatutRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(utilisateurManagementService.changerStatut(id, request.getActif(), authentication.getName()));
    }
}
