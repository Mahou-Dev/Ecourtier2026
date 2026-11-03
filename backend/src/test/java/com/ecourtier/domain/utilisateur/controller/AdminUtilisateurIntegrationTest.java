package com.ecourtier.domain.utilisateur.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.utilisateur.dto.AdminCreateUtilisateurRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangeRoleRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangeStatutRequestDTO;
import com.ecourtier.domain.utilisateur.dto.ChangerMotDePasseRequestDTO;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUtilisateurIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.ecourtier.domain.annonce.repository.AnnonceRepository annonceRepository;

    @Autowired
    private JwtService jwtService;

    private UtilisateurEntity adminUser;
    private UtilisateurEntity secondAdminUser;
    private UtilisateurEntity agentUser;
    private UtilisateurEntity locataireUser;

    private String adminToken;
    private String agentToken;
    private String locataireToken;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        adminUser = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("AdminNom")
                .prenom("AdminPrenom")
                .email("admin.test@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AdminPass123!"))
                .telephone("+221770000001")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        secondAdminUser = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("SecondAdmin")
                .prenom("Prenom")
                .email("admin2.test@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AdminPass123!"))
                .telephone("+221770000002")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        agentUser = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("AgentNom")
                .prenom("AgentPrenom")
                .email("agent.test@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("AgentPass123!"))
                .telephone("+221770000003")
                .role(Role.AGENT)
                .actif(true)
                .build());

        locataireUser = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("LocataireNom")
                .prenom("LocatairePrenom")
                .email("locataire.test@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("LocPass123!"))
                .telephone("+221770000004")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        adminToken = jwtService.generateAccessToken(adminUser);
        agentToken = jwtService.generateAccessToken(agentUser);
        locataireToken = jwtService.generateAccessToken(locataireUser);
    }

    @Test
    @DisplayName("Admin : lister les utilisateurs avec pagination et filtres (rôle, actif, recherche)")
    void testListerUtilisateursParAdmin() throws Exception {
        // Lister tous
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.totalElements").value(4));

        // Filtrer par rôle AGENT
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("agent.test@ecourtier.sn"));

        // Filtrer par recherche textuelle (email ou nom)
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("recherche", "LocataireNom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].role").value("LOCATAIRE"));

        // Filtrer par actif = true
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("actif", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)));

        // Refus aux non-admin (403)
        mockMvc.perform(get("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin : créer un compte AGENT avec mot de passe temporaire généré conforme")
    void testCreerUtilisateurParAdmin() throws Exception {
        AdminCreateUtilisateurRequestDTO request = AdminCreateUtilisateurRequestDTO.builder()
                .prenom("Moussa")
                .nom("Sow")
                .email("moussa.agent@ecourtier.sn")
                .telephone("+221775556677")
                .role(Role.AGENT)
                .build();

        mockMvc.perform(post("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.utilisateur.email").value("moussa.agent@ecourtier.sn"))
                .andExpect(jsonPath("$.utilisateur.role").value("AGENT"))
                .andExpect(jsonPath("$.motDePasseTemporaire", notNullValue()))
                .andExpect(jsonPath("$.motDePasseTemporaire", matchesPattern("^(?=.*[A-Z])(?=.*\\d).{8,}$")));

        // Email en double -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/admin/utilisateurs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Admin : modifier le rôle d'un utilisateur et garde-fous")
    void testModifierRoleUtilisateurEtGardeFous() throws Exception {
        // Changer rôle locataire en PROPRIETAIRE
        ChangeRoleRequestDTO changeRole = ChangeRoleRequestDTO.builder()
                .role(Role.PROPRIETAIRE)
                .build();

        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/role", locataireUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRole)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROPRIETAIRE"));

        // Garde-fou 1 : auto-rétrogradation de son propre compte ADMIN -> 400
        ChangeRoleRequestDTO demoteSelf = ChangeRoleRequestDTO.builder()
                .role(Role.LOCATAIRE)
                .build();

        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/role", adminUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteSelf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("propre rôle")));

        // Garde-fou 2 : rétrograder le dernier admin restant
        // Rétrograder secondAdminUser en AGENT d'abord (il reste adminUser)
        ChangeRoleRequestDTO demoteOther = ChangeRoleRequestDTO.builder()
                .role(Role.AGENT)
                .build();

        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/role", secondAdminUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteOther)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));

        // Maintenant adminUser est le seul ADMIN restant, une tentative depuis un autre token doit échouer
        String secondUserToken = jwtService.generateAccessToken(secondAdminUser);
        // secondAdminUser n'est plus admin, 403
        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/role", adminUser.getId())
                        .header("Authorization", "Bearer " + secondUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteOther)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin : désactivation et réactivation d'un utilisateur, garde-fous et rejet JWT immédiat")
    void testDesactivationEtRejetJwtImmediat() throws Exception {
        // 1. Désactiver locataireUser
        ChangeStatutRequestDTO deactivate = ChangeStatutRequestDTO.builder()
                .actif(false)
                .build();

        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/statut", locataireUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        // 2. Vérification que le locataire désactivé est rejeté immédiatement (403) même avec son jeton encore valide
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("désactivé")));

        // 3. Réactiver locataireUser
        ChangeStatutRequestDTO reactivate = ChangeStatutRequestDTO.builder()
                .actif(true)
                .build();

        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/statut", locataireUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true));

        // 4. Locataire de nouveau autorisé
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + locataireToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("locataire.test@ecourtier.sn"));

        // Garde-fou : auto-désactivation de l'admin refusée (400)
        mockMvc.perform(put("/api/v1/admin/utilisateurs/{id}/statut", adminUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactivate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("propre compte")));
    }

    @Test
    @DisplayName("Utilisateur connecté : consulter son profil et changer son mot de passe")
    void testConsulterProfilEtChangerMotDePasse() throws Exception {
        // GET /api/v1/utilisateurs/moi
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("agent.test@ecourtier.sn"))
                .andExpect(jsonPath("$.role").value("AGENT"));

        // Changement mot de passe réussi
        ChangerMotDePasseRequestDTO changePwd = ChangerMotDePasseRequestDTO.builder()
                .ancienMotDePasse("AgentPass123!")
                .nouveauMotDePasse("NouveauPass456!")
                .build();

        mockMvc.perform(put("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePwd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("succès")));

        // Ancien mot de passe incorrect -> 400
        ChangerMotDePasseRequestDTO badOldPwd = ChangerMotDePasseRequestDTO.builder()
                .ancienMotDePasse("MauvaisAncienPass1!")
                .nouveauMotDePasse("AutrePass789!")
                .build();

        mockMvc.perform(put("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badOldPwd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("ancien mot de passe est incorrect")));
    }
}
