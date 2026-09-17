package com.ecourtier.domain.annonce.controller;

import com.ecourtier.domain.annonce.dto.AnnonceCreateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceUpdateDTO;
import com.ecourtier.domain.annonce.dto.VerificationDecisionDTO;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnnonceIntegrationTest {

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

    private UtilisateurEntity proprietaire1;
    private UtilisateurEntity proprietaire2;
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;

    private String tokenProprietaire1;
    private String tokenProprietaire2;
    private String tokenLocataire;
    private String tokenAdmin;

    @BeforeEach
    void setUp() throws Exception {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        // 1. Create Users
        proprietaire1 = createAndSaveUser("Proprietaire", "Un", "prop1@ecourtier.sn", Role.PROPRIETAIRE);
        proprietaire2 = createAndSaveUser("Proprietaire", "Deux", "prop2@ecourtier.sn", Role.PROPRIETAIRE);
        locataire = createAndSaveUser("Locataire", "User", "locataire@ecourtier.sn", Role.LOCATAIRE);
        admin = createAndSaveUser("Admin", "System", "admin@ecourtier.sn", Role.ADMIN);

        // 2. Login to obtain JWT tokens
        tokenProprietaire1 = obtainToken("prop1@ecourtier.sn", "Pass123!");
        tokenProprietaire2 = obtainToken("prop2@ecourtier.sn", "Pass123!");
        tokenLocataire = obtainToken("locataire@ecourtier.sn", "Pass123!");
        tokenAdmin = obtainToken("admin@ecourtier.sn", "Pass123!");
    }

    private UtilisateurEntity createAndSaveUser(String nom, String prenom, String email, Role role) {
        return utilisateurRepository.save(UtilisateurEntity.builder()
                .nom(nom)
                .prenom(prenom)
                .email(email)
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221 77 000 00 00")
                .role(role)
                .actif(true)
                .build());
    }

    private String obtainToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"motDePasse\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("Test 1: Création d'annonce réussie par PROPRIETAIRE, rejetée pour LOCATAIRE (403)")
    void testCreationAnnoncePermissions() throws Exception {
        AnnonceCreateDTO dto = AnnonceCreateDTO.builder()
                .titre("Appartement F3 Nema")
                .description("Superbe appartement à Nema Ziguinchor")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("150000"))
                .superficie(85.0)
                .nombrePieces(3)
                .quartier("Nema")
                .ville("Ziguinchor")
                .latitude(12.5684)
                .longitude(-16.2719)
                .meuble(true)
                .animauxAcceptes(false)
                .build();

        // A. Token LOCATAIRE -> Rejet 403
        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + tokenLocataire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // B. Token PROPRIETAIRE -> Succès 201
        mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + tokenProprietaire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.statutVerification").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.statutPublication").value("DISPONIBLE"));
    }

    @Test
    @DisplayName("Test 2: Modification d'une annonce par un autre propriétaire rejetée (403)")
    void testModificationAnnonceSecurityOwnership() throws Exception {
        // Propriétaire 1 crée une annonce
        AnnonceCreateDTO dto = AnnonceCreateDTO.builder()
                .titre("Villa Diabir")
                .description("Belle villa")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("250000"))
                .superficie(200.0)
                .nombrePieces(5)
                .quartier("Diabir")
                .ville("Ziguinchor")
                .latitude(12.5510)
                .longitude(-16.2620)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + tokenProprietaire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID annonceId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        // Propriétaire 2 tente d'éditer l'annonce de Propriétaire 1 -> 403 Forbidden
        AnnonceUpdateDTO updateDto = AnnonceUpdateDTO.builder()
                .titre("Titre piraté")
                .prix(new BigDecimal("50000"))
                .build();

        mockMvc.perform(put("/api/v1/annonces/" + annonceId)
                        .header("Authorization", "Bearer " + tokenProprietaire2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());

        // Propriétaire 1 tente d'éditer sa propre annonce -> 200 OK
        mockMvc.perform(put("/api/v1/annonces/" + annonceId)
                        .header("Authorization", "Bearer " + tokenProprietaire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Titre piraté"));
    }

    @Test
    @DisplayName("Test 3: Upload photo > 5MB rejeté (400 Bad Request)")
    void testUploadPhotoTailleExcedee() throws Exception {
        // Créer une annonce
        AnnonceCreateDTO dto = AnnonceCreateDTO.builder()
                .titre("Studio Boucotte")
                .description("Studio meublé")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("95000"))
                .superficie(35.0)
                .nombrePieces(1)
                .quartier("Boucotte")
                .ville("Ziguinchor")
                .latitude(12.5732)
                .longitude(-16.2765)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + tokenProprietaire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID annonceId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        // Créer un fichier factice > 5MB (6MB)
        byte[] largeFileBytes = new byte[6 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile("files", "photo_large.jpg", "image/jpeg", largeFileBytes);

        mockMvc.perform(multipart("/api/v1/annonces/" + annonceId + "/photos")
                        .file(largeFile)
                        .header("Authorization", "Bearer " + tokenProprietaire1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La taille du fichier dépasse la limite maximale autorisée de 5MB"));
    }

    @Test
    @DisplayName("Test 4: Filtres combinés (budgetMin + budgetMax + quartier + meuble) via Specification JPA")
    void testFiltresCombinesSpecification() throws Exception {
        // Créer 2 annonces cibles et 1 annonce non-cible
        AnnonceCreateDTO target1 = AnnonceCreateDTO.builder()
                .titre("Target 1 Nema Meublé")
                .description("Desc")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("140000"))
                .superficie(80.0)
                .nombrePieces(3)
                .quartier("Nema")
                .ville("Ziguinchor")
                .latitude(12.56)
                .longitude(-16.27)
                .meuble(true)
                .build();

        AnnonceCreateDTO target2 = AnnonceCreateDTO.builder()
                .titre("Target 2 Nema Meublé")
                .description("Desc")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("160000"))
                .superficie(90.0)
                .nombrePieces(3)
                .quartier("Nema")
                .ville("Ziguinchor")
                .latitude(12.56)
                .longitude(-16.27)
                .meuble(true)
                .build();

        AnnonceCreateDTO nonTarget = AnnonceCreateDTO.builder()
                .titre("Non Target Almadies Non Meublé")
                .description("Desc")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("500000"))
                .superficie(200.0)
                .nombrePieces(5)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.74)
                .longitude(-17.51)
                .meuble(false)
                .build();

        mockMvc.perform(post("/api/v1/annonces").header("Authorization", "Bearer " + tokenProprietaire1).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(target1))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/annonces").header("Authorization", "Bearer " + tokenProprietaire1).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(target2))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/annonces").header("Authorization", "Bearer " + tokenProprietaire1).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(nonTarget))).andExpect(status().isCreated());

        // Effectuer la recherche combinée: budgetMin=100000, budgetMax=200000, quartier=nema, meuble=true
        mockMvc.perform(get("/api/v1/annonces")
                        .param("budgetMin", "100000")
                        .param("budgetMax", "200000")
                        .param("quartier", "nema")
                        .param("meuble", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].quartier").value("Nema"))
                .andExpect(jsonPath("$.content[0].meuble").value(true));
    }

    @Test
    @DisplayName("Test 5: Attribution du badge VERIFIEE uniquement par l'ADMIN")
    void testBadgeVerificationExclusifAdmin() throws Exception {
        // Création de l'annonce
        AnnonceCreateDTO dto = AnnonceCreateDTO.builder()
                .titre("Appartement Sacré-Cœur")
                .description("Bel appartement")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("320000"))
                .superficie(95.0)
                .nombrePieces(3)
                .quartier("Sacré-Cœur")
                .ville("Dakar")
                .latitude(14.718)
                .longitude(-17.472)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/annonces")
                        .header("Authorization", "Bearer " + tokenProprietaire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutVerification").value("EN_ATTENTE"))
                .andReturn();

        UUID annonceId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        // A. Tentative de validation par le Propriétaire -> 403 Forbidden
        VerificationDecisionDTO decision = VerificationDecisionDTO.builder()
                .statutVerification(StatutVerification.VERIFIEE)
                .build();

        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/verifier")
                        .header("Authorization", "Bearer " + tokenProprietaire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden());

        // B. Validation par l'ADMIN -> 200 OK et statutVerification devient VERIFIEE
        mockMvc.perform(put("/api/v1/admin/annonces/" + annonceId + "/verifier")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutVerification").value("VERIFIEE"));
    }
}
