package com.ecourtier.domain.favori.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.favori.repository.FavoriRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FavoriRepository favoriRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private JwtService jwtService;

    private UtilisateurEntity locataire;
    private AnnonceEntity annonce;
    private String token;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        favoriRepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        favoriRepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Fatou")
                .email("fatou.loc@ecourtier.sn")
                .motDePasse("Pass1234!")
                .telephone("+221771112233")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Plateau Standing")
                .description("Superbe vue mer")
                .prix(BigDecimal.valueOf(450000))
                .superficie(95.0)
                .nombrePieces(3)
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .statutPublication(StatutPublication.DISPONIBLE)
                .ville("Dakar")
                .quartier("Plateau")
                .latitude(14.6738)
                .longitude(-17.4334)
                .utilisateur(locataire)
                .build());

        token = jwtService.generateAccessToken(locataire);
    }

    @Test
    @DisplayName("Workflow complet Favoris : Ajout -> Doublon 409 -> Comptage -> Consultation -> Suppression")
    void testWorkflowFavoris() throws Exception {
        // 1. Ajouter l'annonce en favori -> 201 Created
        mockMvc.perform(post("/api/v1/annonces/{id}/favoris", annonce.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // 2. Tenter un ajout en double -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/annonces/{id}/favoris", annonce.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // 3. Compter les favoris -> count = 1
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris/count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // 4. Lister les favoris -> contient l'annonce avec estFavori = true
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(annonce.getId().toString()))
                .andExpect(jsonPath("$.content[0].titre").value("Appartement Plateau Standing"))
                .andExpect(jsonPath("$.content[0].estFavori").value(true));

        // 5. Supprimer le favori -> 204 No Content
        mockMvc.perform(delete("/api/v1/annonces/{id}/favoris", annonce.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 6. Re-compter les favoris -> count = 0
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris/count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
