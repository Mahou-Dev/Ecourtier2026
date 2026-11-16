package com.ecourtier.domain.annonce.controller;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.DocumentVerificationEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutDocument;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.entity.TypeDocumentVerification;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.repository.DocumentVerificationRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DocumentVerificationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private DocumentVerificationRepository documentVerificationRepository;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity owner;
    private AnnonceEntity annonce;
    private DocumentVerificationEntity doc;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();

        owner = utilisateurRepository.save(UtilisateurEntity.builder()
                .prenom("Owner")
                .nom("Proprio")
                .email("owner.sec@test.sn")
                .motDePasse("Pass1234!")
                .telephone("+221770001111")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Annonce Securisee")
                .description("Description securisee")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(50.0)
                .nombrePieces(2)
                .quartier("Fann")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.46)
                .statutVerification(StatutVerification.EN_ATTENTE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(owner)
                .build());

        doc = documentVerificationRepository.save(DocumentVerificationEntity.builder()
                .annonce(annonce)
                .typeDocument(TypeDocumentVerification.TITRE_FONCIER)
                .nomFichier("titre_secret_cadastral.pdf")
                .url("/uploads/docs/secret.pdf")
                .statut(StatutDocument.REJETE)
                .motifRejet("Cachet notarial illisible sur la copie")
                .build());

        annonce.setDocumentsVerification(new java.util.ArrayList<>(java.util.List.of(doc)));
        annonce = annonceRepository.saveAndFlush(annonce);
    }

    @Test
    @WithMockUser(username = "stranger.locataire@test.sn", roles = {"LOCATAIRE"})
    @DisplayName("Refus 403 lors de l'accès à un document cadastral par un tiers non-propriétaire")
    void testDocumentAccessDeniedToThirdParty() throws Exception {
        mockMvc.perform(get("/api/v1/annonces/{id}/documents/{docId}/fichier", annonce.getId(), doc.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner.sec@test.sn", roles = {"PROPRIETAIRE"})
    @DisplayName("Accès autorisé au document cadastral pour le propriétaire de l'annonce")
    void testDocumentAccessAllowedToOwner() throws Exception {
        // En test sans fichier physique, le service retournera NOT_FOUND ou OK si le fichier existe
        // L'important ici est d'avoir dépassé la sécurité 403 Forbidden
        mockMvc.perform(get("/api/v1/annonces/{id}/documents/{docId}/fichier", annonce.getId(), doc.getId()))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("ANO-04 : Visiteur anonyme ne reçoit aucune métadonnée de document justificatif")
    void testAnonymousDoesNotReceiveDocumentMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/annonces/{id}", annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(annonce.getId().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification").isEmpty());

        mockMvc.perform(get("/api/v1/annonces"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].documentsVerification").isEmpty());
    }

    @Test
    @WithMockUser(username = "stranger.locataire@test.sn", roles = {"LOCATAIRE"})
    @DisplayName("ANO-04 : Utilisateur authentifié non-propriétaire ne reçoit aucune métadonnée de document justificatif")
    void testNonOwnerDoesNotReceiveDocumentMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/annonces/{id}", annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification").isEmpty());

        mockMvc.perform(get("/api/v1/annonces"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].documentsVerification").isEmpty());
    }

    @Test
    @WithMockUser(username = "owner.sec@test.sn", roles = {"PROPRIETAIRE"})
    @DisplayName("ANO-04 : Le propriétaire de l'annonce reçoit les métadonnées de ses pièces justificatives")
    void testOwnerReceivesDocumentMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/annonces/{id}", annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification[0].typeDocument").value("TITRE_FONCIER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification[0].nomFichier").value("titre_secret_cadastral.pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification[0].motifRejet").value("Cachet notarial illisible sur la copie"));
    }

    @Test
    @WithMockUser(username = "admin.sec@test.sn", roles = {"ADMIN"})
    @DisplayName("ANO-04 : Un administrateur reçoit les métadonnées des pièces justificatives")
    void testAdminReceivesDocumentMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/annonces/{id}", annonce.getId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification[0].typeDocument").value("TITRE_FONCIER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification[0].nomFichier").value("titre_secret_cadastral.pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentsVerification[0].motifRejet").value("Cachet notarial illisible sur la copie"));
    }
}
