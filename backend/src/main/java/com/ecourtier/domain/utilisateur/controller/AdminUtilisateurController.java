package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Administration Utilisateurs", description = "Endpoints réservés aux administrateurs E-Courtier")
@SecurityRequirement(name = "bearerAuth")
public class AdminUtilisateurController {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateurMapper utilisateurMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister tous les utilisateurs (ADMIN uniquement)", description = "Route d'administration protégée réservée exclusivement aux utilisateurs ayant le rôle ADMIN.")
    public ResponseEntity<List<UtilisateurResponseDTO>> listerTousLesUtilisateurs() {
        List<UtilisateurResponseDTO> utilisateurs = utilisateurRepository.findAll()
                .stream()
                .map(utilisateurMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(utilisateurs);
    }
}
