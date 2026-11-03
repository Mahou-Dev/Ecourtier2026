package com.ecourtier.domain.admin.controller;

import com.ecourtier.domain.utilisateur.dto.ConnexionRequestDTO;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStatistiquesControllerTest {

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

    private String adminToken;
    private String locataireToken;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        // Admin User
        UtilisateurEntity admin = UtilisateurEntity.builder()
                .nom("Sarr")
                .prenom("Aminata")
                .email("admin.stat@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AdminPass123!"))
                .telephone("+221 33 999 00 00")
                .role(Role.ADMIN)
                .actif(true)
                .build();
        utilisateurRepository.save(admin);

        // Locataire User
        UtilisateurEntity locataire = UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Ousmane")
                .email("locataire.stat@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("UserPass123!"))
                .telephone("+221 77 111 00 00")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build();
        utilisateurRepository.save(locataire);

        // Get admin token
        MvcResult adminRes = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO("admin.stat@ecourtier.sn", "AdminPass123!"))))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(adminRes.getResponse().getContentAsString()).get("accessToken").asText();

        // Get locataire token
        MvcResult locRes = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConnexionRequestDTO("locataire.stat@ecourtier.sn", "UserPass123!"))))
                .andExpect(status().isOk())
                .andReturn();
        locataireToken = objectMapper.readTree(locRes.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("Accès admin/statistiques par un non-admin renvoie 403 Forbidden")
    void testAccesNonAdminRefuse() throws Exception {
        mockMvc.perform(get("/api/v1/admin/statistiques")
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Accès non authentifié renvoie 401 Unauthorized")
    void testAccesNonAuthentifieRefuse() throws Exception {
        mockMvc.perform(get("/api/v1/admin/statistiques"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Accès admin/statistiques par ADMIN renvoie 200 OK avec payload complet")
    void testAccesAdminAutorise() throws Exception {
        mockMvc.perform(get("/api/v1/admin/statistiques?periode=30j")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periode").value("30j"))
                .andExpect(jsonPath("$.utilisateurs", notNullValue()))
                .andExpect(jsonPath("$.annonces", notNullValue()))
                .andExpect(jsonPath("$.activiteCommerciale", notNullValue()))
                .andExpect(jsonPath("$.moderation", notNullValue()))
                .andExpect(jsonPath("$.assistantIA", notNullValue()));
    }
}
