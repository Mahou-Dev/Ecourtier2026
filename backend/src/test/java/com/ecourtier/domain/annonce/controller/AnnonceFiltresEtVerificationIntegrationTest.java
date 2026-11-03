package com.ecourtier.domain.annonce.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.dto.AnnonceCreateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceUpdateDTO;
import com.ecourtier.domain.annonce.dto.VerificationDecisionDTO;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnonceFiltresEtVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private UtilisateurEntity proprietaireAuteur;
    private UtilisateurEntity proprietaireTiers;
    private UtilisateurEntity admin;

    private String auteurToken;
    private String tiersToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        proprietaireAuteur = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Moussa")
                .email("auteur@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770001122")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        proprietaireTiers = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sow")
                .prenom("Fatou")
                .email("tiers@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770003344")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Systeme")
                .email("admin@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770005566")
                .role(Role.ADMIN)
                .actif(true)
                .build());

        auteurToken = jwtService.generateAccessToken(proprietaireAuteur);
        tiersToken = jwtService.generateAccessToken(proprietaireTiers);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/annonces/quartiers : autocomplétion des quartiers disponibles")
    void testGetQuartiersDisponibles() throws Exception {
        // Créer deux annonces avec des quartiers différents
        AnnonceCreateDTO dto1 = AnnonceCreateDTO.builder()
                .titre("Appartement 1")
                .description("Desc 1")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(50.0)
                .nombrePieces(2)
                .ville("Dakar")
                .quartier("Mermoz")
                .latitude(14.71)
                .longitude(-17.47)
                .build();

        AnnonceCreateDTO dto2 = AnnonceCreateDTO.builder()
                .titre("Appartement 2")
                .description("Desc 2")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(300000))
                .superficie(70.0)
                .nombrePieces(3)
                .ville("Dakar")
                .quartier("Almadies")
                .latitude(14.74)
                .longitude(-17.52)
                .build();

        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/annonces/quartiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Mermoz")))
                .andExpect(jsonPath("$", hasItem("Almadies")));
    }

    @Test
    @DisplayName("Filtre : paramètre d'énumération inconnu renvoie 400 clair")
    void testParametreEnumInconnuRenvoie400() throws Exception {
        mockMvc.perform(get("/api/v1/annonces")
                        .param("modeTransaction", "TROQUER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("invalide")));

        mockMvc.perform(get("/api/v1/annonces")
                        .param("type", "MANOIR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("invalide")));
    }

    @Test
    @DisplayName("Validation géographique : coordonnées au Sénégal acceptées, hors Sénégal rejetées")
    void testValidationCoordonneesGeographiquesSenegal() throws Exception {
        // Coordonnées à Paris (hors Sénégal) -> 400
        AnnonceCreateDTO horsSenegal = AnnonceCreateDTO.builder()
                .titre("Studio hors pays")
                .description("Coordonnées en France")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(100000))
                .superficie(30.0)
                .nombrePieces(1)
                .ville("Dakar")
                .quartier("Fann")
                .latitude(48.8566)
                .longitude(2.3522)
                .build();

        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(horsSenegal)))
                .andExpect(status().isBadRequest());

        // Coordonnées à Dakar (au Sénégal) -> 201
        AnnonceCreateDTO auSenegal = AnnonceCreateDTO.builder()
                .titre("Studio au pays")
                .description("Coordonnées valides")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(100000))
                .superficie(30.0)
                .nombrePieces(1)
                .ville("Dakar")
                .quartier("Fann")
                .latitude(14.69)
                .longitude(-17.46)
                .build();

        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auSenegal)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Droits de modification et suppression : Auteur OK, Admin OK, Tiers 403")
    void testDroitsModificationEtSuppression() throws Exception {
        AnnonceCreateDTO createDto = AnnonceCreateDTO.builder()
                .titre("Villa Fann Résidence")
                .description("Description originale")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.VENTE)
                .prix(BigDecimal.valueOf(80000000))
                .superficie(300.0)
                .nombrePieces(6)
                .ville("Dakar")
                .quartier("Fann")
                .latitude(14.69)
                .longitude(-17.46)
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID annonceId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());

        AnnonceUpdateDTO updateDto = AnnonceUpdateDTO.builder()
                .titre("Villa Fann Modifiée")
                .description("Description mise à jour")
                .prix(BigDecimal.valueOf(85000000))
                .superficie(300.0)
                .nombrePieces(6)
                .ville("Dakar")
                .quartier("Fann")
                .latitude(14.69)
                .longitude(-17.46)
                .build();

        // 1. Tiers tente de modifier -> 403 Forbidden
        mockMvc.perform(put("/api/v1/annonces/{id}", annonceId)
                        .header("Authorization", "Bearer " + tiersToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());

        // 2. Auteur modifie -> 200 OK
        mockMvc.perform(put("/api/v1/annonces/{id}", annonceId)
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Villa Fann Modifiée"));

        // 3. Tiers tente de supprimer -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/annonces/{id}", annonceId)
                        .header("Authorization", "Bearer " + tiersToken))
                .andExpect(status().isForbidden());

        // 4. Admin supprime -> 204 No Content
        mockMvc.perform(delete("/api/v1/annonces/{id}", annonceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Pièces justificatives : PDF accepté (10 Mo) pour document, rejeté pour photo, rejet motif court")
    void testPiecesJustificativesPdfEtValidation() throws Exception {
        AnnonceCreateDTO createDto = AnnonceCreateDTO.builder()
                .titre("Bien test documents")
                .description("Vérification documents")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(60.0)
                .nombrePieces(2)
                .ville("Dakar")
                .quartier("Point E")
                .latitude(14.70)
                .longitude(-17.46)
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + auteurToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID annonceId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());

        // 1. PDF rejeté pour une PHOTO d'annonce (photos = images uniquement)
        byte[] pdfBytes = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfPhoto = new MockMultipartFile("files", "photo.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/v1/annonces/{id}/photos", annonceId)
                        .file(pdfPhoto)
                        .header("Authorization", "Bearer " + auteurToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsStringIgnoringCase("format")));

        // 2. PDF accepté pour un DOCUMENT DE VÉRIFICATION (<= 10 Mo)
        MockMultipartFile pdfDoc = new MockMultipartFile("file", "bail_senelec.pdf", "application/pdf", pdfBytes);

        MvcResult uploadDocRes = mockMvc.perform(multipart("/api/v1/annonces/{id}/documents", annonceId)
                        .file(pdfDoc)
                        .param("typeDocument", "BAIL_PRECEDENT")
                        .header("Authorization", "Bearer " + auteurToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeDocument").value("BAIL_PRECEDENT"))
                .andExpect(jsonPath("$.nomFichier").value("bail_senelec.pdf"))
                .andReturn();

        UUID docId = UUID.fromString(objectMapper.readTree(uploadDocRes.getResponse().getContentAsString()).get("id").asText());

        // 3. Téléchargement du document PDF avec Content-Type application/pdf
        mockMvc.perform(get("/api/v1/annonces/{id}/documents/{documentId}/fichier", annonceId, docId)
                        .header("Authorization", "Bearer " + auteurToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));

        // 4. Décision REJETEE avec motif trop court (< 10 caractères) -> 400
        VerificationDecisionDTO shortReject = VerificationDecisionDTO.builder()
                .statutVerification(StatutVerification.REJETEE)
                .motifRejet("Court")
                .build();

        mockMvc.perform(put("/api/v1/admin/annonces/{id}/verifier", annonceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortReject)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("10 caractères")));

        // 5. Décision REJETEE avec motif valide (>= 10 caractères) -> 200
        VerificationDecisionDTO validReject = VerificationDecisionDTO.builder()
                .statutVerification(StatutVerification.REJETEE)
                .motifRejet("Document illisible et flou.")
                .build();

        mockMvc.perform(put("/api/v1/admin/annonces/{id}/verifier", annonceId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVerification").value("REJETEE"));
    }
}
