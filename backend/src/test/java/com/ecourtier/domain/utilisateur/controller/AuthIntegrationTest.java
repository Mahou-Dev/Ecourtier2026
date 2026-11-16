package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
import com.ecourtier.domain.utilisateur.dto.InscriptionRequestDTO;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private com.ecourtier.domain.transaction.repository.TransactionRepository transactionRepository;

    @Autowired
    private com.ecourtier.domain.rendezvous.repository.RendezVousRepository rendezVousRepository;

    @Autowired
    private com.ecourtier.domain.annonce.repository.AnnonceRepository annonceRepository;

    @Autowired
    private com.ecourtier.domain.avis.repository.SignalementRepository signalementRepository;

    @Autowired
    private com.ecourtier.domain.avis.repository.AvisRepository avisRepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.ConversationIARepository conversationIARepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.MessageIARepository messageIARepository;

    @Autowired
    private com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.ecourtier.domain.favori.repository.FavoriRepository favoriRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.ecourtier.common.security.LoginRateLimiterService loginRateLimiterService;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    @SuppressWarnings("unchecked")
    private void clearLoginRateLimiter() {
        Object attempts = org.springframework.test.util.ReflectionTestUtils.getField(loginRateLimiterService, "attemptsMap");
        Object ipAttempts = org.springframework.test.util.ReflectionTestUtils.getField(loginRateLimiterService, "ipAttemptsMap");
        if (attempts instanceof java.util.Map<?, ?> map) {
            map.clear();
        }
        if (ipAttempts instanceof java.util.Map<?, ?> map) {
            map.clear();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        clearLoginRateLimiter();
        testDatabaseCleaner.cleanAll();
    }

    @BeforeEach
    void setUp() {
        clearLoginRateLimiter();
        testDatabaseCleaner.cleanAll();
    }

    @Test
    @DisplayName("Workflow Inscription -> Connexion -> Récupération de Tokens")
    void testInscriptionEtConnexionWorkflow() throws Exception {
        InscriptionRequestDTO inscriptionRequest = InscriptionRequestDTO.builder()
                .nom("Diallo")
                .prenom("Awa")
                .email("awa.diallo@ecourtier.sn")
                .motDePasse("Pass1234Secure!")
                .telephone("+221 77 890 12 34")
                .role(Role.LOCATAIRE)
                .build();

        // 1. Inscription réussie
        mockMvc.perform(post("/api/v1/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inscriptionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.utilisateur.email").value("awa.diallo@ecourtier.sn"))
                .andExpect(jsonPath("$.utilisateur.role").value("LOCATAIRE"));

        // 2. Connexion avec les bons identifiants
        ConnexionRequestDTO connexionRequest = ConnexionRequestDTO.builder()
                .email("awa.diallo@ecourtier.sn")
                .motDePasse("Pass1234Secure!")
                .build();

        mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connexionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.utilisateur.email").value("awa.diallo@ecourtier.sn"));
    }

    @Test
    @DisplayName("Connexion avec mauvais mot de passe rejetée (401)")
    void testConnexionMauvaisMotDePasseRejetee() throws Exception {
        // Inscrire un utilisateur
        UtilisateurEntity user = UtilisateurEntity.builder()
                .nom("Sow")
                .prenom("Ibrahima")
                .email("ibrahima.sow@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221 76 543 21 00")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build();
        utilisateurRepository.save(user);

        // Tenter connexion avec mauvais mot de passe
        ConnexionRequestDTO wrongConnexion = ConnexionRequestDTO.builder()
                .email("ibrahima.sow@ecourtier.sn")
                .motDePasse("MauvaisPassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongConnexion)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Contrôle d'accès par rôle sur route /api/v1/admin/utilisateurs")
    void testControleAccesRoleAdmin() throws Exception {
        // 1. Tenter accès sans token -> Accès Refusé (401 ou 403)
        mockMvc.perform(get("/api/v1/admin/utilisateurs"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403, "Devrait retourner 401 ou 403 pour un accès non authentifié");
                });

        // 2. Inscrire un utilisateur LOCATAIRE et obtenir son token
        InscriptionRequestDTO locataireRequest = InscriptionRequestDTO.builder()
                .nom("Faye")
                .prenom("Modou")
                .email("modou.faye@ecourtier.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 111 22 33")
                .role(Role.LOCATAIRE)
                .build();

        MvcResult locataireResult = mockMvc.perform(post("/api/v1/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locataireRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String locataireToken = objectMapper.readTree(locataireResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Tenter accès route admin avec token LOCATAIRE -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isForbidden());

        // 3. Créer un utilisateur ADMIN en base de données et se connecter
        UtilisateurEntity adminUser = UtilisateurEntity.builder()
                .nom("Sène")
                .prenom("Babacar")
                .email("admin.sene@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AdminPass123!"))
                .telephone("+221 33 800 00 00")
                .role(Role.ADMIN)
                .actif(true)
                .build();
        utilisateurRepository.save(adminUser);

        ConnexionRequestDTO adminConnexion = ConnexionRequestDTO.builder()
                .email("admin.sene@ecourtier.sn")
                .motDePasse("AdminPass123!")
                .build();

        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminConnexion)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(adminResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Accès avec token ADMIN -> 200 OK
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @DisplayName("ANO-06 : Un client ne peut pas contourner le rate limiter en forgeant des en-têtes X-Forwarded-For distincts")
    void testRateLimiterCannotBeBypassedWithSpoofedXForwardedFor() throws Exception {
        UtilisateurEntity user = UtilisateurEntity.builder()
                .nom("Gueye")
                .prenom("Ousmane")
                .email("ousmane.spoof@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221 77 999 88 77")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build();
        utilisateurRepository.save(user);

        ConnexionRequestDTO wrongConnexion = ConnexionRequestDTO.builder()
                .email("ousmane.spoof@ecourtier.sn")
                .motDePasse("WrongPass123!")
                .build();

        // 5 tentatives échouées en changeant X-Forwarded-For à chaque appel -> 401
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/connexion")
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongConnexion)))
                    .andExpect(status().isUnauthorized());
        }

        // 6ème tentative avec un nouveau X-Forwarded-For forgé -> 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/connexion")
                        .header("X-Forwarded-For", "198.51.100.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongConnexion)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("P0-1 : Un refreshToken est rejeté en 401 sur les endpoints REST protégés et un accessToken est rejeté sur /rafraichir")
    void testStrictSeparationAccessVsRefreshTokensSurRest() throws Exception {
        InscriptionRequestDTO dto = InscriptionRequestDTO.builder()
                .nom("Sarr")
                .prenom("Aissatou")
                .email("aissatou.p01@ecourtier.sn")
                .motDePasse("Pass1234!")
                .telephone("+221 77 222 11 00")
                .role(Role.PROPRIETAIRE)
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        var json = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();
        String refreshToken = json.get("refreshToken").asText();

        // 1. L'accessToken accède normalement à un endpoint protégé
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("aissatou.p01@ecourtier.sn"));

        // 2. Le refreshToken est STRICTEMENT refusé (401 Unauthorized) sur les endpoints REST protégés
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/transactions/mes-transactions")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + refreshToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 3. Un accessToken envoyé sur /auth/rafraichir est rejeté en 401 Unauthorized
        mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // 4. Un refreshToken valide fonctionne normalement sur /auth/rafraichir et délivre un accessToken opérationnel
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/rafraichir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        String newAccessToken = objectMapper.readTree(refreshRes.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("aissatou.p01@ecourtier.sn"));
    }
}
