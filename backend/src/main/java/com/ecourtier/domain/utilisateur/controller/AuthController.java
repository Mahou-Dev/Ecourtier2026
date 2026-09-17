package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Endpoints publics de création de compte, connexion et rafraîchissement de token JWT")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/inscription")
    @Operation(summary = "Inscription d'un nouvel utilisateur", description = "Création d'un compte utilisateur avec le rôle PROPRIETAIRE ou LOCATAIRE.")
    public ResponseEntity<TokenResponseDTO> inscription(@Valid @RequestBody InscriptionRequestDTO request) {
        TokenResponseDTO response = authService.inscription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/connexion")
    @Operation(summary = "Connexion utilisateur", description = "Authentification par email/mot de passe et génération d'un couple de tokens JWT (access + refresh).")
    public ResponseEntity<TokenResponseDTO> connexion(@Valid @RequestBody ConnexionRequestDTO request) {
        TokenResponseDTO response = authService.connexion(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rafraichir")
    @Operation(summary = "Rafraîchir le token d'accès JWT", description = "Obtenir un nouveau token d'accès en fournissant un refresh token valide.")
    public ResponseEntity<TokenResponseDTO> rafraichir(@Valid @RequestBody RefreshTokenRequestDTO request) {
        TokenResponseDTO response = authService.rafraichir(request);
        return ResponseEntity.ok(response);
    }
}
