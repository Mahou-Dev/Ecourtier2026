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
    private com.ecourtier.domain.annonce.repository.AnnonceRepository annonceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
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
}
