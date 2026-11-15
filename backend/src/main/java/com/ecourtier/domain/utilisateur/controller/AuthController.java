package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecourtier.common.security.LoginRateLimiterService;
import com.ecourtier.common.security.RefreshRateLimiterService;
import com.ecourtier.domain.utilisateur.dto.DeconnexionRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Endpoints publics de création de compte, connexion, rafraîchissement (rotation) et révocation de token JWT")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final RefreshRateLimiterService refreshRateLimiterService;

    @PostMapping("/inscription")
    @SecurityRequirements
    @Operation(summary = "Inscription d'un nouvel utilisateur", description = "Création d'un compte utilisateur avec le rôle PROPRIETAIRE ou LOCATAIRE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compte créé et couple (accessToken + refreshToken) émis"),
            @ApiResponse(responseCode = "400", description = "Données d'inscription invalides ou politique de mot de passe non respectée"),
            @ApiResponse(responseCode = "409", description = "Adresse email déjà associée à un compte existant")
    })
    public ResponseEntity<TokenResponseDTO> inscription(@Valid @RequestBody InscriptionRequestDTO request) {
        TokenResponseDTO response = authService.inscription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/connexion")
    @SecurityRequirements
    @Operation(summary = "Connexion utilisateur", description = "Authentification par email/mot de passe et génération d'un couple de tokens JWT (access + refresh).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentification réussie (accessToken + refreshToken émis)"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Identifiants invalides ou compte désactivé"),
            @ApiResponse(responseCode = "429", description = "Trop de tentatives de connexion depuis cette IP / cet email")
    })
    public ResponseEntity<TokenResponseDTO> connexion(
            @Valid @RequestBody ConnexionRequestDTO request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        loginRateLimiterService.checkAndRecordAttempt(clientIp, request.getEmail());

        TokenResponseDTO response = authService.connexion(request);
        loginRateLimiterService.resetAttempts(clientIp, request.getEmail());
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
    }

    @PostMapping("/rafraichir")
    @SecurityRequirements
    @Operation(summary = "Rafraîchir le token d'accès JWT", description = "Rotation stricte : le refresh token fourni est consommé et un nouveau couple (access + refresh) est émis. Toute réutilisation d'un refresh token déjà consommé révoque toute la session (401). Limité en débit par IP (429).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nouveau couple (accessToken + refreshToken) émis après rotation"),
            @ApiResponse(responseCode = "400", description = "Refresh token manquant"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalide, expiré, révoqué, rejoué ou compte désactivé"),
            @ApiResponse(responseCode = "429", description = "Limite de rafraîchissements par IP dépassée")
    })
    public ResponseEntity<TokenResponseDTO> rafraichir(
            @Valid @RequestBody RefreshTokenRequestDTO request,
            HttpServletRequest httpRequest
    ) {
        refreshRateLimiterService.checkAndRecordAttempt(getClientIp(httpRequest));
        TokenResponseDTO response = authService.rafraichir(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deconnexion")
    @SecurityRequirements
    @Operation(summary = "Déconnexion serveur", description = "Révoque la famille du refresh token fourni ou, à défaut, tous les refresh tokens actifs de l'utilisateur authentifié par son access token. Retourne 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session(s) révoquée(s) côté serveur"),
            @ApiResponse(responseCode = "401", description = "Access token invalide si fourni sans refresh token")
    })
    public ResponseEntity<Void> deconnexion(
            @RequestBody(required = false) DeconnexionRequestDTO request,
            HttpServletRequest httpRequest
    ) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        Object familyIdAttr = httpRequest != null ? httpRequest.getAttribute("jwtFamilyId") : null;
        java.util.UUID authenticatedFamilyId = familyIdAttr instanceof java.util.UUID uuid ? uuid : null;
        authService.deconnecter(refreshToken, currentAuthenticatedEmail(), authenticatedFamilyId);
        return ResponseEntity.noContent().build();
    }

    private String currentAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
