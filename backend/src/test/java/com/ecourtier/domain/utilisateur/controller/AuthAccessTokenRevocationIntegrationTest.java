package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.utilisateur.dto.ChangeRoleRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangeStatutRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangerMotDePasseRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.DeconnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.RefreshTokenRequestDTO;
import com.ecourtier.domain.utilisateur.dto.TokenResponseDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAccessTokenRevocationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private UtilisateurEntity user;
    private UtilisateurEntity admin;
    private String initialPassword;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        initialPassword = "Password123!";
        user = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Awa")
                .email("awa." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode(initialPassword))
                .telephone("+22177" + Math.abs(suffix.hashCode() % 10000000))
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());
        createdUserIds.add(user.getId());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Admin")
                .email("admin." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode(initialPassword))
                .telephone("+22178" + Math.abs(suffix.hashCode() % 10000000))
                .role(Role.ADMIN)
                .actif(true)
                .build());
        createdUserIds.add(admin.getId());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdUserIds) {
            refreshTokenRepository.deleteAll(
                    refreshTokenRepository.findAll().stream()
                            .filter(rt -> rt.getUtilisateur() != null && id.equals(rt.getUtilisateur().getId()))
                            .toList()
            );
            utilisateurRepository.findById(id).ifPresent(utilisateurRepository::delete);
        }
        createdUserIds.clear();
    }

    private TokenResponseDTO login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponseDTO.class);
    }

    @Test
    @DisplayName("Phase 3 A1: Révocation immédiate de l'access token après déconnexion + rotation refresh préservée")
    void testAccessTokenImmediatelyRevokedAfterLogoutAndRefreshRotationPreserved() throws Exception {
        TokenResponseDTO initialTokens = login(user.getEmail(), initialPassword);
        String firstAccessToken = initialTokens.getAccessToken();
        String firstRefreshToken = initialTokens.getRefreshToken();

        assertEquals(0L, jwtService.extractTokenVersion(firstAccessToken));

        // 1. Access token valide accepté sur REST
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));

        // 2. Un REFRESH token ne doit jamais être accepté en tant que Bearer ACCESS
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + firstRefreshToken))
                .andExpect(status().isUnauthorized());

        // 3. Rotation refresh token toujours fonctionnelle avant déconnexion
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(firstRefreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponseDTO rotatedTokens = objectMapper.readValue(refreshRes.getResponse().getContentAsString(), TokenResponseDTO.class);
        assertNotEquals(firstRefreshToken, rotatedTokens.getRefreshToken());

        // 4. Déconnexion avec le refresh courant (actif)
        mockMvc.perform(post("/api/v1/auth/deconnexion")
                        .header("Authorization", "Bearer " + rotatedTokens.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeconnexionRequestDTO(rotatedTokens.getRefreshToken()))))
                .andExpect(status().isNoContent());

        // 5. Réutilisation immédiate des deux anciens access tokens -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + firstAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/conversations/mes-conversations")
                        .header("Authorization", "Bearer " + rotatedTokens.getAccessToken()))
                .andExpect(status().isUnauthorized());

        // L'ancien refresh consommé et le refresh révoqué échouent tous deux en 401
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(firstRefreshToken))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(rotatedTokens.getRefreshToken()))))
                .andExpect(status().isUnauthorized());

        // 6. Nouvelle connexion obtient tokenVersion=1 et refonctionne
        TokenResponseDTO freshTokens = login(user.getEmail(), initialPassword);
        assertEquals(1L, jwtService.extractTokenVersion(freshTokens.getAccessToken()));
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + freshTokens.getAccessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA3-001: Le rejeu d'un Refresh Token consommé révoque immédiatement les Access Tokens de la session compromise sans déconnecter un autre appareil actif")
    void testRefreshTokenReplayImmediatelyRevokesCompromisedSessionAccessTokensWhilePreservingOtherDeviceSession() throws Exception {
        // Session A (appareil 1) et Session B (appareil 2)
        TokenResponseDTO sessionA1 = login(user.getEmail(), initialPassword);
        TokenResponseDTO sessionB = login(user.getEmail(), initialPassword);

        // Rotation normale sur l'appareil A : R1_A -> R2_A
        MvcResult rotRes = mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(sessionA1.getRefreshToken()))))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponseDTO sessionA2 = objectMapper.readValue(rotRes.getResponse().getContentAsString(), TokenResponseDTO.class);

        // Avant le rejeu, les Access Tokens A1, A2 et B sont tous acceptés
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + sessionA1.getAccessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + sessionA2.getAccessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + sessionB.getAccessToken()))
                .andExpect(status().isOk());

        // Rejeu frauduleux du Refresh Token déjà consommé R1_A -> 401
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(sessionA1.getRefreshToken()))))
                .andExpect(status().isUnauthorized());

        // CA3-001 : Les Access Tokens A1 et A2 de la session compromise sont immédiatement rejetés en 401
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + sessionA1.getAccessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + sessionA2.getAccessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/conversations/mes-conversations")
                        .header("Authorization", "Bearer " + sessionA2.getAccessToken()))
                .andExpect(status().isUnauthorized());

        // Le Refresh Token R2_A de la famille compromise est également rejeté en 401
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(sessionA2.getRefreshToken()))))
                .andExpect(status().isUnauthorized());

        // L'appareil B indépendant conserve sa session REST et sa capacité de rotation
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + sessionB.getAccessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(sessionB.getRefreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA3-002: Un ancien Refresh Token expiré, déjà consommé ou déjà révoqué est rejeté en 401 sur /deconnexion et /rafraichir sans déconnecter la session active")
    void testOldRevokedConsumedOrExpiredRefreshTokenCannotForceLogoutActiveSession() throws Exception {
        // 1. Créer une première session, faire une rotation R1 -> R2 puis la déconnecter proprement
        TokenResponseDTO oldSession1 = login(user.getEmail(), initialPassword);
        String consumedRefresh = oldSession1.getRefreshToken();

        MvcResult rotRes = mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(consumedRefresh))))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponseDTO oldSession2 = objectMapper.readValue(rotRes.getResponse().getContentAsString(), TokenResponseDTO.class);
        String revokedRefresh = oldSession2.getRefreshToken();

        mockMvc.perform(post("/api/v1/auth/deconnexion")
                        .header("Authorization", "Bearer " + oldSession2.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeconnexionRequestDTO(revokedRefresh))))
                .andExpect(status().isNoContent());

        // 2. Créer une session destinée à expirer en base
        TokenResponseDTO expiringSession = login(user.getEmail(), initialPassword);
        String expiredRefresh = expiringSession.getRefreshToken();
        UUID expiredJti = UUID.fromString(jwtService.extractJti(expiredRefresh));
        refreshTokenRepository.findAll().stream()
                .filter(rt -> expiredJti.equals(rt.getJti()))
                .findFirst()
                .ifPresent(rt -> {
                    rt.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(10));
                    refreshTokenRepository.saveAndFlush(rt);
                });

        // 3. La victime ouvre sa nouvelle session active légitime
        TokenResponseDTO activeVictimSession = login(user.getEmail(), initialPassword);
        long tokenVersionBeforeAttacks = utilisateurRepository.findById(user.getId()).orElseThrow().getTokenVersion();

        // 4. Tentatives de DoS sur /api/v1/auth/deconnexion avec jetons consommé, révoqué, expiré et inconnu -> toutes rejetées en 401
        for (String staleToken : List.of(consumedRefresh, revokedRefresh, expiredRefresh, "forged.refresh.token")) {
            mockMvc.perform(post("/api/v1/auth/deconnexion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new DeconnexionRequestDTO(staleToken))))
                    .andExpect(status().isUnauthorized());
        }

        // Tentatives sur /api/v1/auth/rafraichir avec jetons révoqué et expiré -> 401 sans effet de bord
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(revokedRefresh))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(expiredRefresh))))
                .andExpect(status().isUnauthorized());

        // 5. Vérifier que token_version n'a PAS été incrémenté et que la session active de la victime fonctionne toujours
        long tokenVersionAfterAttacks = utilisateurRepository.findById(user.getId()).orElseThrow().getTokenVersion();
        assertEquals(tokenVersionBeforeAttacks, tokenVersionAfterAttacks,
                "Aucun ancien refresh token ne doit incrémenter token_version ni révoquer la session active");

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + activeVictimSession.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(activeVictimSession.getRefreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Phase 3 A1: Changement de mot de passe invalide immédiatement les anciens access et refresh tokens")
    void testAccessTokenImmediatelyRevokedAfterPasswordChange() throws Exception {
        TokenResponseDTO tokens = login(user.getEmail(), initialPassword);
        String oldAccessToken = tokens.getAccessToken();
        String oldRefreshToken = tokens.getRefreshToken();

        // Vérifier accès préalable valide
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isOk());

        String newPassword = "NewPassword456!";
        mockMvc.perform(put("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangerMotDePasseRequestDTO(initialPassword, newPassword))))
                .andExpect(status().isOk());

        // Réutilisation immédiate de l'ancien access token rejetée -> 401
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());

        // Réutilisation de l'ancien refresh token rejetée -> 401
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(oldRefreshToken))))
                .andExpect(status().isUnauthorized());

        // Reconnexion avec ancien mot de passe refusée, nouveau accepté
        mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO(user.getEmail(), initialPassword))))
                .andExpect(status().isUnauthorized());

        TokenResponseDTO postChangeTokens = login(user.getEmail(), newPassword);
        assertEquals(1L, jwtService.extractTokenVersion(postChangeTokens.getAccessToken()));
        assertNotNull(postChangeTokens.getAccessToken());
    }

    @Test
    @DisplayName("Phase 3 A1: Désactivation du compte invalide immédiatement l'access token existant même avec tokenVersion incrémenté")
    void testAccessTokenImmediatelyRevokedAfterAccountDeactivation() throws Exception {
        TokenResponseDTO userTokens = login(user.getEmail(), initialPassword);
        TokenResponseDTO adminTokens = login(admin.getEmail(), initialPassword);
        String userAccessToken = userTokens.getAccessToken();
        String userRefreshToken = userTokens.getRefreshToken();

        // Avant désactivation, le jeton fonctionne
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isOk());

        // Désactivation par ADMIN
        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/statut", user.getId())
                        .header("Authorization", "Bearer " + adminTokens.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatutRequestDTO(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        // Réutilisation immédiate de l'access token -> 401 ou 403
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().is(anyOf(is(401), is(403))));

        // Refresh rejeté
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(userRefreshToken))))
                .andExpect(status().is(anyOf(is(401), is(403))));

        // Nouvelle tentative de connexion refusée
        mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO(user.getEmail(), initialPassword))))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("CA3-013 : Changement de rôle utilisateur révoque immédiatement access token et refresh token")
    void testAccessTokenAndRefreshRevokedAfterRoleChange() throws Exception {
        TokenResponseDTO userTokens = login(user.getEmail(), initialPassword);
        TokenResponseDTO adminTokens = login(admin.getEmail(), initialPassword);
        String oldAccessToken = userTokens.getAccessToken();
        String oldRefreshToken = userTokens.getRefreshToken();

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/role", user.getId())
                        .header("Authorization", "Bearer " + adminTokens.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequestDTO(Role.PROPRIETAIRE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROPRIETAIRE"));

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + oldAccessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(oldRefreshToken))))
                .andExpect(status().isUnauthorized());

        TokenResponseDTO postChangeTokens = login(user.getEmail(), initialPassword);
        assertEquals(1L, jwtService.extractTokenVersion(postChangeTokens.getAccessToken()));
        assertEquals("PROPRIETAIRE", postChangeTokens.getUtilisateur().getRole().name());
    }
}
