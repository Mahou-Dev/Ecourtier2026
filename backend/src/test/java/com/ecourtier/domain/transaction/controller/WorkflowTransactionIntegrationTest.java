package com.ecourtier.domain.transaction.controller;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.dto.TransactionRequestDTO;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.ecourtier.domain.transaction.service.PdfBailGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WorkflowTransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PdfBailGeneratorService pdfBailGeneratorService;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity tiers;
    private AnnonceEntity annonce;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        rendezVousRepository.deleteAll();
        annonceRepository.deleteAll();

        proprietaire = utilisateurRepository.findByEmail("prop.tx.test@ecourtier.sn")
                .orElseGet(() -> utilisateurRepository.save(UtilisateurEntity.builder()
                        .nom("Sow")
                        .prenom("Ousmane")
                        .email("prop.tx.test@ecourtier.sn")
                        .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                        .telephone("+221772223344")
                        .role(Role.PROPRIETAIRE)
                        .actif(true)
                        .build()));

        locataire = utilisateurRepository.findByEmail("loc.tx.test@ecourtier.sn")
                .orElseGet(() -> utilisateurRepository.save(UtilisateurEntity.builder()
                        .nom("Diop")
                        .prenom("Awa")
                        .email("loc.tx.test@ecourtier.sn")
                        .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                        .telephone("+221773334455")
                        .role(Role.LOCATAIRE)
                        .actif(true)
                        .build()));

        tiers = utilisateurRepository.findByEmail("tiers.tx.test@ecourtier.sn")
                .orElseGet(() -> utilisateurRepository.save(UtilisateurEntity.builder()
                        .nom("Ndiaye")
                        .prenom("Ibrahima")
                        .email("tiers.tx.test@ecourtier.sn")
                        .motDePasse("$2a$10$KhUqg3EWfmNgLhTIGhjP0uzeSXRI1y0ulUhiJyv1Qz0H34Ybju3IS")
                        .telephone("+221774445566")
                        .role(Role.LOCATAIRE)
                        .actif(true)
                        .build()));

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Sacré-Cœur F4")
                .description("Superbe appartement 4 pièces")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(110.0)
                .nombrePieces(4)
                .quartier("Sacré-Cœur")
                .ville("Dakar")
                .latitude(14.7167)
                .longitude(-17.4677)
                .meuble(true)
                .animauxAcceptes(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    @Test
    @DisplayName("Test 1 : Transaction refusée (HTTP 400) si aucun RDV EFFECTUÉ préalable n'existe")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testTransactionRefuseeSiAucunRdvEffectuePrealable() throws Exception {
        TransactionRequestDTO dto = TransactionRequestDTO.builder()
                .montantCaution(BigDecimal.valueOf(250000))
                .build();

        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 2 : Libération automatique du séquestre déclenchée UNIQUEMENT quand bail SIGNE_COMPLET + séquestre BLOQUE")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testLiberationAutomatiqueSequestreBailSigneComplet() throws Exception {
        // 1. Créer un RDV EFFECTUE
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // 2. Initier la transaction
        TransactionRequestDTO dto = TransactionRequestDTO.builder()
                .montantCaution(BigDecimal.valueOf(250000))
                .build();

        MvcResult txResult = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andExpect(jsonPath("$.statutBail", is("NON_SIGNE")))
                .andExpect(jsonPath("$.montantCaution").value(250000))
                .andExpect(jsonPath("$.montantPremierLoyer").value(250000))
                .andExpect(jsonPath("$.fraisDossier").value(5000))
                .andExpect(jsonPath("$.montantTotalSequestre").value(505000))
                .andExpect(jsonPath("$.montantVerseProprietaire").value(500000))
                .andReturn();

        String txId = objectMapper.readTree(txResult.getResponse().getContentAsString()).get("id").asText();

        // 3. Simuler paiement caution Wave (statutSequestre -> BLOQUE)
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/paiement-caution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.referenceWave", notNullValue()));

        // 4. Première signature par le locataire (statutBail -> SIGNE_LOCATAIRE, séquestre reste BLOQUE)
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/bail/signer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        // 5. Seconde signature par le propriétaire (statutBail -> SIGNE_COMPLET => DÉCLENCHE LIBÉRATION AUTOMATIQUE)
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/bail/signer")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prop.tx.test@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_COMPLET")))
                .andExpect(jsonPath("$.statutSequestre", is("LIBERE")))
                .andExpect(jsonPath("$.montantTotalSequestre").value(505000))
                .andExpect(jsonPath("$.montantVerseProprietaire").value(500000))
                .andExpect(jsonPath("$.dateLiberation", notNullValue()))
                .andExpect(jsonPath("$.pdfBailPath", notNullValue()));

        // Vérifier que le statut de l'annonce est passé à LOUEE
        AnnonceEntity annonceUpdated = annonceRepository.findById(annonce.getId()).orElseThrow();
        assertEquals(StatutPublication.LOUEE, annonceUpdated.getStatutPublication());
    }

    @Test
    @DisplayName("Test 4 : Accès au bail PDF refusé à un tiers (HTTP 403), autorisé aux parties prenantes (HTTP 200)")
    void testAccesBailPdfRefuseTiersAutoriseParties() throws Exception {
        // Préparer une transaction avec PDF généré
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .pdfBailPath("uploads/baux/bail_test.pdf")
                .build());

        // Créer un fichier de test physique
        java.io.File file = new java.io.File("uploads/baux/bail_test.pdf");
        file.getParentFile().mkdirs();
        file.createNewFile();

        // 1. Accès refusé à un tiers (HTTP 403)
        mockMvc.perform(get("/api/v1/transactions/" + tx.getId() + "/bail/pdf")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tiers.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isForbidden());

        // 2. Accès autorisé au locataire partie prenante (HTTP 200)
        mockMvc.perform(get("/api/v1/transactions/" + tx.getId() + "/bail/pdf")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk());

        // 3. Accès autorisé au propriétaire partie prenante (HTTP 200)
        mockMvc.perform(get("/api/v1/transactions/" + tx.getId() + "/bail/pdf")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prop.tx.test@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk());

        file.delete();
    }

    @Test
    @DisplayName("Test 5 : Tentative de double signature par le même signataire rejetée (HTTP 400)")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testDoubleSignatureParMemePartieRejetee() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .statutBail(StatutBail.NON_SIGNE)
                .build());

        // 1. Première signature par le locataire -> 200 OK, statut SIGNE_LOCATAIRE
        mockMvc.perform(post("/api/v1/transactions/" + tx.getId() + "/bail/signer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")));

        // 2. Seconde signature par LE MÊME locataire -> 400 Bad Request, statut reste SIGNE_LOCATAIRE
        mockMvc.perform(post("/api/v1/transactions/" + tx.getId() + "/bail/signer"))
                .andExpect(status().isBadRequest());

        // Vérifier en base que le statut est resté SIGNE_LOCATAIRE (pas SIGNE_COMPLET)
        TransactionEntity txUpdated = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutBail.SIGNE_LOCATAIRE, txUpdated.getStatutBail());
    }

    @Test
    @DisplayName("Test 6 : Calcul des montants LOCATION et montant client forgé expressément ignoré")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testCalculMontantsLocationEtMontantForgeIgnore() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // Le client envoie un montant falsifié (ex: montantCaution = 1 FCFA)
        String payloadFalsifie = "{\"montantCaution\": 1}";

        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadFalsifie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andExpect(jsonPath("$.montantCaution").value(250000))
                .andExpect(jsonPath("$.montantPremierLoyer").value(250000))
                .andExpect(jsonPath("$.fraisDossier").value(5000))
                .andExpect(jsonPath("$.montantTotalSequestre").value(505000))
                .andExpect(jsonPath("$.montantVerseProprietaire").value(500000));
    }

    @Test
    @DisplayName("Test 7 (F29) : VENTE à 45 000 000 FCFA donne un acompte de 4 500 000 FCFA, ignore le montant forgé et verse l'acompte au vendeur")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testCalculMontantsVente45MillionsAcompte10PourcentEtLiberation() throws Exception {
        AnnonceEntity annonceVente = annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Fann Résidence")
                .description("Grande villa standing en vente")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.VENTE)
                .prix(BigDecimal.valueOf(45000000))
                .superficie(350.0)
                .nombrePieces(7)
                .quartier("Fann")
                .ville("Dakar")
                .latitude(14.69)
                .longitude(-17.47)
                .meuble(false)
                .animauxAcceptes(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());

        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonceVente)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // Montant client falsifié (1 FCFA) envoyé exprès -> doit être ignoré au profit de 10 % de 45 000 000 = 4 500 000 FCFA
        MvcResult txRes = mockMvc.perform(post("/api/v1/annonces/" + annonceVente.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantCaution\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modeTransaction", is("VENTE")))
                .andExpect(jsonPath("$.acomptePourcentage").value(10))
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andExpect(jsonPath("$.montantCaution").value(4500000))
                .andExpect(jsonPath("$.montantPremierLoyer").value(0))
                .andExpect(jsonPath("$.fraisDossier").value(5000))
                .andExpect(jsonPath("$.montantTotalSequestre").value(4505000))
                .andExpect(jsonPath("$.montantVerseProprietaire").value(4500000))
                .andReturn();

        String txVenteId = objectMapper.readTree(txRes.getResponse().getContentAsString()).get("id").asText();

        // Paiement Wave Sandbox + double signature -> le vendeur reçoit 4 500 000 FCFA, la plateforme garde 5 000 FCFA
        mockMvc.perform(post("/api/v1/transactions/" + txVenteId + "/paiement-caution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/" + txVenteId + "/bail/signer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_LOCATAIRE")));

        mockMvc.perform(post("/api/v1/transactions/" + txVenteId + "/bail/signer")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prop.tx.test@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutBail", is("SIGNE_COMPLET")))
                .andExpect(jsonPath("$.statutSequestre", is("LIBERE")))
                .andExpect(jsonPath("$.montantTotalSequestre").value(4505000))
                .andExpect(jsonPath("$.montantVerseProprietaire").value(4500000))
                .andExpect(jsonPath("$.fraisDossier").value(5000));

        AnnonceEntity updatedVente = annonceRepository.findById(annonceVente.getId()).orElseThrow();
        assertEquals(StatutPublication.VENDUE, updatedVente.getStatutPublication());
    }

    @Test
    @DisplayName("Test 8 (F29) : Vérification des libellés PDF en LOCATION et en VENTE")
    void testPdfContientDetailConditionsFinancieresEtLibellesLocationEtVente() throws Exception {
        // 1. PDF LOCATION
        TransactionEntity txLocation = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .referenceWave("WAVE-TEST-999")
                .build();

        String pdfLocPath = pdfBailGeneratorService.generatePdfBail(txLocation);
        java.io.File pdfLocFile = new java.io.File(pdfLocPath);
        assertTrue(pdfLocFile.exists());
        assertTrue(pdfLocFile.length() > 0);

        com.lowagie.text.pdf.PdfReader readerLoc = new com.lowagie.text.pdf.PdfReader(new java.io.FileInputStream(pdfLocFile));
        String textLoc = new com.lowagie.text.pdf.parser.PdfTextExtractor(readerLoc).getTextFromPage(1);
        readerLoc.close();

        assertTrue(textLoc.contains("CONTRAT DE BAIL NUMÉRIQUE E-COURTIER SÉNÉGAL"));
        assertTrue(textLoc.contains("Document récapitulatif généré par la plateforme"));
        assertTrue(!textLoc.contains("il ne constitue pas un acte de vente"));
        assertTrue(textLoc.contains("CONDITIONS FINANCIÈRES"));
        assertTrue(textLoc.contains("250000"));
        assertTrue(textLoc.contains("5000"));
        assertTrue(textLoc.contains("505000"));
        assertTrue(textLoc.contains("500000"));
        pdfLocFile.delete();

        // 2. PDF VENTE
        AnnonceEntity annonceVente = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Villa Almadies Vente")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.VENTE)
                .prix(BigDecimal.valueOf(45000000))
                .quartier("Almadies")
                .ville("Dakar")
                .utilisateur(proprietaire)
                .build();

        TransactionEntity txVente = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonceVente)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(4500000))
                .montantPremierLoyer(BigDecimal.ZERO)
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .referenceWave("WAVE-VENTE-45M")
                .build();

        String pdfVentePath = pdfBailGeneratorService.generatePdfBail(txVente);
        java.io.File pdfVenteFile = new java.io.File(pdfVentePath);
        assertTrue(pdfVenteFile.exists());
        assertTrue(pdfVenteFile.length() > 0);

        com.lowagie.text.pdf.PdfReader readerVente = new com.lowagie.text.pdf.PdfReader(new java.io.FileInputStream(pdfVenteFile));
        String textVente = new com.lowagie.text.pdf.parser.PdfTextExtractor(readerVente).getTextFromPage(1);
        readerVente.close();

        assertTrue(textVente.contains("RÉCAPITULATIF DE RÉSERVATION D'ACHAT"));
        assertTrue(textVente.contains("Document récapitulatif généré par la plateforme ; il ne constitue pas un acte de vente"));
        assertTrue(textVente.contains("Acompte de réservation (10 % du prix) : 4500000 FCFA"));
        assertTrue(textVente.contains("4505000"));
        pdfVenteFile.delete();
    }
}
