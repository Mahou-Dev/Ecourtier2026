package com.ecourtier.domain.avis.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.dto.SignalementCreateDTO;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.AvisRepository;
import com.ecourtier.domain.avis.repository.SignalementRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvisAndSignalementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignalementRepository signalementRepository;

    @Autowired
    private AvisRepository avisRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;
    private AnnonceEntity annonce;

    private String locataireToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        signalementRepository.deleteAll();
        avisRepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sow")
                .prenom("Ibrahima")
                .email("sow.prop@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770001111")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Aminata")
                .email("diallo.loc@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770002222")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Modérateur")
                .email("admin.mod@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770003333")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Fann Résidence")
                .description("Grande villa de maître")
                .prix(BigDecimal.valueOf(1200000))
                .superficie(250.0)
                .nombrePieces(5)
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .statutPublication(StatutPublication.DISPONIBLE)
                .ville("Dakar")
                .quartier("Fann")
                .latitude(14.69)
                .longitude(-17.46)
                .utilisateur(proprietaire)
                .build());

        locataireToken = jwtService.generateAccessToken(locataire);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        signalementRepository.deleteAll();
        avisRepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @Test
    @DisplayName("Avis public & Réputation utilisateur")
    void testAvisEtReputationUtilisateur() throws Exception {
        // 1. Obtenir les avis d'une annonce (aucun avis au début)
        mockMvc.perform(get("/api/v1/annonces/{id}/avis", annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreAvis").value(0))
                .andExpect(jsonPath("$.noteMoyenne").value(nullValue()));

        // 2. Obtenir la réputation du propriétaire
        mockMvc.perform(get("/api/v1/utilisateurs/{id}/reputation", proprietaire.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateurId").value(proprietaire.getId().toString()))
                .andExpect(jsonPath("$.nombreAvis").value(0))
                .andExpect(jsonPath("$.noteMoyenne").value(nullValue()));
    }

    @Test
    @DisplayName("Workflow signalement : Créer -> Lister admin -> Traiter")
    void testWorkflowSignalementEtModeration() throws Exception {
        // 1. Locataire signale une annonce suspecte
        SignalementCreateDTO createDto = SignalementCreateDTO.builder()
                .motif(MotifSignalement.PRIX_TROMPEUR)
                .description("Le prix affiché ne correspond pas à la réalité.")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/annonces/{id}/signalements", annonce.getId())
                        .header("Authorization", "Bearer " + locataireToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.motif").value("PRIX_TROMPEUR"))
                .andReturn();

        UUID signalementId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());

        // 2. Admin liste les signalements
        mockMvc.perform(get("/api/v1/admin/signalements")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("statut", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(signalementId.toString()));

        // 3. Admin traite le signalement
        TraiterSignalementDTO traiterDto = TraiterSignalementDTO.builder()
                .statut(StatutSignalement.REJETE)
                .suspendreAnnonce(false)
                .build();

        mockMvc.perform(put("/api/v1/admin/signalements/{id}/traiter", signalementId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(traiterDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REJETE"));
    }
}
