package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.common.security.RefreshRateLimiterService;
import com.ecourtier.domain.utilisateur.dto.ChangeStatutRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangerMotDePasseRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.entity.RefreshTokenEntity;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.ecourtier.domain.utilisateur.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthPhase2SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.ConversationIARepository conversationIARepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.MessageIARepository messageIARepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshRateLimiterService refreshRateLimiterService;

    @Value("${ecourtier.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    @BeforeEach
    @AfterEach
    void cleanup() {
        testDatabaseCleaner.cleanAll();
        refreshRateLimiterService.resetAttempts("198.51.100.88");
        refreshRateLimiterService.resetAttempts("127.0.0.1");
    }

    private JsonNode registerAndGetTokens(String email, Role role) throws Exception {
        InscriptionRequestDTO dto = InscriptionRequestDTO.builder()
                .nom("Test")
                .prenom("Phase2")
                .email(email)
                .motDePasse("Pass1234!")
                .telephone("+221770001122")
                .role(role)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("RES-05 (1) : Rotation refresh token R1 -> R2 et détection de rejeu de R1 révoque toute la famille")
    void refreshRotationAndReplayFamilyRevocation() throws Exception {
        JsonNode initial = registerAndGetTokens("rotation.p2@ecourtier.sn", Role.LOCATAIRE);
        String r1 = initial.get("refreshToken").asText();
        UUID jti1 = UUID.fromString(jwtService.extractJti(r1));

        // 1. Rotation R1 -> R2
        MvcResult rot1 = mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode json2 = objectMapper.readTree(rot1.getResponse().getContentAsString());
        String r2 = json2.get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);
        UUID jti2 = UUID.fromString(jwtService.extractJti(r2));

        RefreshTokenEntity entity1 = refreshTokenRepository.findById(
                refreshTokenRepository.findByFamilyId(
                        refreshTokenRepository.findAll().get(0).getFamilyId()).stream()
                        .filter(e -> jti1.equals(e.getJti())).findFirst().orElseThrow().getId()
        ).orElseThrow();
        assertThat(entity1.getUsedAt()).isNotNull();
        assertThat(entity1.getReplacedByJti()).isEqualTo(jti2);

        // 2. Rejeu frauduleux de R1 -> 401 ET révocation immédiate de toute la famille (y compris R2)
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isUnauthorized());

        List<RefreshTokenEntity> familyTokens = refreshTokenRepository.findByFamilyId(entity1.getFamilyId());
        assertThat(familyTokens).hasSize(2);
        assertThat(familyTokens).allSatisfy(token -> {
            assertThat(token.getRevokedAt()).isNotNull();
            assertThat(token.getRevocationReason()).isEqualTo(AuthService.REVOCATION_REUSE_DETECTED);
        });

        // 3. R2 (pourtant jamais utilisé par le client légitime) est maintenant rejeté en 401
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RES-05 (2) : Concurrence sur /rafraichir avec le même token — un seul 200, l'autre 401")
    void concurrentRefreshSameToken_OnlyOneSucceeds() throws Exception {
        JsonNode initial = registerAndGetTokens("concurrent.p2@ecourtier.sn", Role.LOCATAIRE);
        String r1 = initial.get("refreshToken").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final String ip = "198.51.100." + (10 + i);
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/auth/rafraichir")
                                .with(req -> {
                                    req.setRemoteAddr(ip);
                                    return req;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + r1 + "\"}"))
                        .andReturn().getResponse().getStatus();
            };
            futures.add(pool.submit(task));
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        List<RefreshTokenEntity> all = refreshTokenRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
    }

    @Test
    @DisplayName("RES-05 (3) : /deconnexion révoque la famille de tokens et rejette tout futur /rafraichir")
    void logoutRevokesRefreshFamily() throws Exception {
        JsonNode initial = registerAndGetTokens("logout.p2@ecourtier.sn", Role.PROPRIETAIRE);
        String accessToken = initial.get("accessToken").asText();
        String r1 = initial.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/deconnexion")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + r1 + "\"}"))
                .andExpect(status().isUnauthorized());

        List<RefreshTokenEntity> stored = refreshTokenRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getRevocationReason()).isEqualTo(AuthService.REVOCATION_LOGOUT);
    }

    @Test
    @DisplayName("RES-05 (4) : Rate limiter /rafraichir bloque après dépassement -> 429 Too Many Requests")
    void refreshRateLimitEnforced() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/rafraichir")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.88");
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"invalid\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .with(req -> {
                            req.setRemoteAddr("198.51.100.88");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("RES-05 (5) : Rejet token refresh hérité sans jti et token avec mauvais issuer/audience")
    void rejectLegacyRefreshWithoutJtiAndWrongIssuerAudience() throws Exception {
        UtilisateurEntity user = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Legacy").prenom("User").email("legacy.p2@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221770002233").role(Role.LOCATAIRE).actif(true).build());

        SecretKey key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        Date exp = new Date(now.getTime() + 600_000L);

        String legacyRefresh = Jwts.builder()
                .subject(user.getEmail())
                .issuer("ecourtier-api")
                .audience().add("ecourtier-client").and()
                .claim("type", JwtService.TOKEN_TYPE_REFRESH)
                .issuedAt(now).expiration(exp).signWith(key).compact();

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + legacyRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        String wrongAudienceAccess = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .issuer("ecourtier-api")
                .audience().add("evil-client").and()
                .claim("type", JwtService.TOKEN_TYPE_ACCESS)
                .issuedAt(now).expiration(exp).signWith(key).compact();

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + wrongAudienceAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RES-05 (6) : Désactivation compte et changement mot de passe révoquent tous les refresh tokens actifs")
    void disableAccountAndPasswordChangeRevokeTokens() throws Exception {
        // Admin pour désactiver
        utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin").prenom("Secu").email("admin.p2@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AdminPass123!"))
                .telephone("+221770009000").role(Role.ADMIN).actif(true).build());

        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO("admin.p2@ecourtier.sn", "AdminPass123!"))))
                .andExpect(status().isOk()).andReturn();
        String adminAccess = objectMapper.readTree(adminLogin.getResponse().getContentAsString()).get("accessToken").asText();

        JsonNode userTokens = registerAndGetTokens("target.p2@ecourtier.sn", Role.LOCATAIRE);
        String userAccess = userTokens.get("accessToken").asText();
        String userRefresh = userTokens.get("refreshToken").asText();
        UUID userId = UUID.fromString(userTokens.get("utilisateur").get("id").asText());

        // Changement de mot de passe -> révoque userRefresh
        ChangerMotDePasseRequestDTO pwdReq = new ChangerMotDePasseRequestDTO();
        pwdReq.setAncienMotDePasse("Pass1234!");
        pwdReq.setNouveauMotDePasse("NewPass1234!");

        mockMvc.perform(put("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + userAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pwdReq)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + userRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // Reconnexion + désactivation admin -> révoque le nouveau refresh token
        MvcResult userRelogin = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO("target.p2@ecourtier.sn", "NewPass1234!"))))
                .andExpect(status().isOk()).andReturn();
        String freshRefresh = objectMapper.readTree(userRelogin.getResponse().getContentAsString()).get("refreshToken").asText();

        ChangeStatutRequestDTO disableReq = new ChangeStatutRequestDTO();
        disableReq.setActif(false);
        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/statut", userId)
                        .header("Authorization", "Bearer " + adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + freshRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
