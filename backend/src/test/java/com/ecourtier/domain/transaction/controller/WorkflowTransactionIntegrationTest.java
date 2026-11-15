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

    @Autowired
    private com.ecourtier.domain.transaction.client.WaveClientMock waveClientMock;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity tiers;
    private AnnonceEntity annonce;

    @BeforeEach
    void setUp() {
        waveClientMock.resetState();
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

    @Test
    @DisplayName("ANO-01 : Idempotence du paiement de caution — refus d'un second paiement et préservation de la référence et des dates")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testPaiementCautionIdempotenceEtRefusSecondPaiement() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        MvcResult txResult = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String txId = objectMapper.readTree(txResult.getResponse().getContentAsString()).get("id").asText();

        // 1. Premier paiement valide -> 200 OK
        MvcResult firstPayResult = mockMvc.perform(post("/api/v1/transactions/" + txId + "/paiement-caution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.referenceWave", notNullValue()))
                .andExpect(jsonPath("$.datePaiement", notNullValue()))
                .andReturn();

        String firstWaveRef = objectMapper.readTree(firstPayResult.getResponse().getContentAsString()).get("referenceWave").asText();
        TransactionEntity txAfterFirstPay = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();
        LocalDateTime persistedDatePaiement = txAfterFirstPay.getDatePaiement();

        // 2. Deuxième paiement sur la transaction déjà BLOQUE -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/paiement-caution"))
                .andExpect(status().isConflict());

        // Vérifier que la référence Wave et la date de paiement n'ont pas été écrasées
        TransactionEntity txAfterRetry = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();
        assertEquals(StatutSequestre.BLOQUE, txAfterRetry.getStatutSequestre());
        assertEquals(firstWaveRef, txAfterRetry.getReferenceWave());
        assertEquals(persistedDatePaiement, txAfterRetry.getDatePaiement());

        // 3. Double signature -> LIBERE, puis nouvelle tentative de paiement sur état LIBERE -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/bail/signer"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/bail/signer")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prop.tx.test@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("LIBERE")));

        TransactionEntity txReleased = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();
        LocalDateTime originalDateLiberation = txReleased.getDateLiberation();
        String originalPdfPath = txReleased.getPdfBailPath();

        mockMvc.perform(post("/api/v1/transactions/" + txId + "/paiement-caution"))
                .andExpect(status().isConflict());

        TransactionEntity txAfterReleasedRetry = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();
        assertEquals(StatutSequestre.LIBERE, txAfterReleasedRetry.getStatutSequestre());
        assertEquals(firstWaveRef, txAfterReleasedRetry.getReferenceWave());
        assertEquals(originalDateLiberation, txAfterReleasedRetry.getDateLiberation());
        assertEquals(originalPdfPath, txAfterReleasedRetry.getPdfBailPath());
    }

    @Test
    @DisplayName("ANO-01 : Concurrence sur le paiement de caution — deux appels simultanés ne déclenchent qu'un seul paiement")
    void testConcurrencePaiementCaution() throws Exception {
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
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .build());

        waveClientMock.resetCounters();

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<Integer> statuses = new java.util.concurrent.CopyOnWriteArrayList<>();

        Runnable payTask = () -> {
            ready.countDown();
            try {
                start.await();
                MvcResult res = mockMvc.perform(post("/api/v1/transactions/" + tx.getId() + "/paiement-caution")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                        .andReturn();
                statuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                statuses.add(500);
            }
        };

        executor.submit(payTask);
        executor.submit(payTask);
        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        long okCount = statuses.stream().filter(s -> s == 200).count();
        long conflictCount = statuses.stream().filter(s -> s == 409).count();
        assertEquals(1, okCount, "Un seul appel concurrent doit réussir (200 OK)");
        assertEquals(1, conflictCount, "Le second appel concurrent doit être rejeté (409 Conflict)");
        assertEquals(1, waveClientMock.getCheckoutCallCount(), "waveClient.initiateEscrowCheckout(...) doit être appelé exactement 1 fois");

        TransactionEntity updated = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.BLOQUE, updated.getStatutSequestre());
        assertTrue(updated.getReferenceWave() != null && updated.getReferenceWave().startsWith("WAVE-SANDBOX-"));
    }

    @Test
    @DisplayName("ANO-02 : Refus d'initialisation de transaction sur annonce non vérifiée, rejetée ou indisponible")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testRefusAnnonceNonVerifieeRejeteeOuIndisponible() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // 1. Annonce EN_ATTENTE -> 400 Bad Request
        annonce.setStatutVerification(StatutVerification.EN_ATTENTE);
        annonce = annonceRepository.save(annonce);

        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // 2. Annonce REJETEE -> 400 Bad Request
        annonce.setStatutVerification(StatutVerification.REJETEE);
        annonce = annonceRepository.save(annonce);

        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // 3. Annonce VERIFIEE mais LOUEE / RESERVEE / SUSPENDUE -> 409 Conflict
        annonce.setStatutVerification(StatutVerification.VERIFIEE);
        for (StatutPublication statutIndispo : java.util.List.of(
                StatutPublication.RESERVEE,
                StatutPublication.LOUEE,
                StatutPublication.VENDUE,
                StatutPublication.SUSPENDUE)) {
            annonce.setStatutPublication(statutIndispo);
            annonce = annonceRepository.save(annonce);

            mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("ANO-02 : Refus d'une deuxième transaction active incompatible, mais réouverture autorisée après remboursement")
    @WithMockUser(username = "loc.tx.test@ecourtier.sn", roles = {"LOCATAIRE"})
    void testRefusDeuxiemeTransactionActiveEtAutorisationApresRemboursement() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(tiers)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // 1. Première transaction créée avec succès -> 201 Created
        MvcResult firstTxRes = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String firstTxId = objectMapper.readTree(firstTxRes.getResponse().getContentAsString()).get("id").asText();

        // 2. Deuxième transaction sur la même annonce (par le même locataire ou un autre) -> 409 Conflict
        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tiers.tx.test@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        // 3. Paiement puis remboursement de la première transaction (statut -> REMBOURSE)
        mockMvc.perform(post("/api/v1/transactions/" + firstTxId + "/paiement-caution"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transactions/" + firstTxId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Désistement amiable avant signature complète\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")));

        // 4. La transaction remboursée ne bloque plus l'annonce disponible -> nouvelle transaction acceptée (201 Created)
        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tiers.tx.test@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")));
    }

    @Test
    @DisplayName("ANO-02 : Concurrence à l'initialisation — deux requêtes simultanées ne créent qu'une seule transaction active")
    void testConcurrenceInitialisationTransactionSurMemeAnnonce() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(tiers)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<Integer> statuses = new java.util.concurrent.CopyOnWriteArrayList<>();

        Runnable taskLocataire1 = () -> {
            ready.countDown();
            try {
                start.await();
                MvcResult res = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn();
                statuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                statuses.add(500);
            }
        };

        Runnable taskLocataire2 = () -> {
            ready.countDown();
            try {
                start.await();
                MvcResult res = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tiers.tx.test@ecourtier.sn").roles("LOCATAIRE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn();
                statuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                statuses.add(500);
            }
        };

        executor.submit(taskLocataire1);
        executor.submit(taskLocataire2);
        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        long createdCount = statuses.stream().filter(s -> s == 201).count();
        long conflictCount = statuses.stream().filter(s -> s == 409).count();
        assertEquals(1, createdCount, "Une seule transaction active doit être créée (201 Created)");
        assertEquals(1, conflictCount, "La demande concurrente doit être refusée (409 Conflict)");
        assertEquals(1, transactionRepository.findByAnnonceIdOrderByDateCreationDesc(annonce.getId()).size());
    }

    @Test
    @DisplayName("ANO-09 : Workflow complet de remboursement, contrôle d'accès, conservation des références et blocage sur états invalides")
    void testWorkflowRemboursementCompletEtSecurite() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // 1. Créer une transaction (INITIALISE)
        MvcResult createRes = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        String txId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // 2. Refus du remboursement d'une transaction non payée (INITIALISE) -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Tentative sur non payé\"}"))
                .andExpect(status().isConflict());

        // 3. Paiement de la caution -> BLOQUE
        MvcResult payRes = mockMvc.perform(post("/api/v1/transactions/" + txId + "/paiement-caution")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andReturn();

        String initialWaveRef = objectMapper.readTree(payRes.getResponse().getContentAsString()).get("referenceWave").asText();

        // 4. Contrôle d'autorisations : LOCATAIRE, PROPRIETAIRE et AGENT ne peuvent pas déclencher un remboursement -> 403 Forbidden
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/transactions/" + txId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("prop.tx.test@ecourtier.sn").roles("PROPRIETAIRE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/transactions/" + txId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("agent.tx.test@ecourtier.sn").roles("AGENT")))
                .andExpect(status().isForbidden());

        // 5. Remboursement autorisé par ADMIN -> 200 OK, statut REMBOURSE, conservation de referenceWave et ajout de referenceRemboursementWave
        MvcResult refundRes = mockMvc.perform(post("/api/v1/transactions/" + txId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Annulation validée par l'administration\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")))
                .andExpect(jsonPath("$.referenceWave", is(initialWaveRef)))
                .andExpect(jsonPath("$.referenceRemboursementWave", notNullValue()))
                .andExpect(jsonPath("$.montantRembourse").value(505000))
                .andExpect(jsonPath("$.dateRemboursement", notNullValue()))
                .andExpect(jsonPath("$.motifRemboursement", is("Annulation validée par l'administration")))
                .andReturn();

        String firstRefundRef = objectMapper.readTree(refundRes.getResponse().getContentAsString()).get("referenceRemboursementWave").asText();

        // 6. Deuxième remboursement refusé -> 409 Conflict, référence de remboursement inchangée
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN")))
                .andExpect(status().isConflict());

        TransactionEntity txAfterSecondRefund = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();
        assertEquals(initialWaveRef, txAfterSecondRefund.getReferenceWave());
        assertEquals(firstRefundRef, txAfterSecondRefund.getReferenceRemboursementWave());
    }

    @Test
    @DisplayName("ANO-09 : Refus du remboursement d'une transaction dont les fonds ont déjà été libérés (LIBERE) et concurrence sur remboursement")
    void testRefusRemboursementApresLiberationEtConcurrenceRemboursement() throws Exception {
        // Partie A : Transaction déjà LIBERE -> remboursement refusé (409 Conflict)
        TransactionEntity txLiberee = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .referenceWave("WAVE-SANDBOX-REL999")
                .dateLiberation(LocalDateTime.now())
                .statutBail(StatutBail.SIGNE_COMPLET)
                .build());

        mockMvc.perform(post("/api/v1/transactions/" + txLiberee.getId() + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN")))
                .andExpect(status().isConflict());

        // Partie B : Concurrence de deux appels simultanés de remboursement sur une transaction BLOQUE réellement payée
        TransactionEntity txBloquee = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .build());

        MvcResult payRes = mockMvc.perform(post("/api/v1/transactions/" + txBloquee.getId() + "/paiement-caution")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andReturn();
        String paidWaveRef = objectMapper.readTree(payRes.getResponse().getContentAsString()).get("referenceWave").asText();

        waveClientMock.resetCounters();

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<Integer> statuses = new java.util.concurrent.CopyOnWriteArrayList<>();

        Runnable refundTask = () -> {
            ready.countDown();
            try {
                start.await();
                MvcResult res = mockMvc.perform(post("/api/v1/transactions/" + txBloquee.getId() + "/remboursement")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"motif\": \"Test concurrence remboursement\"}"))
                        .andReturn();
                statuses.add(res.getResponse().getStatus());
            } catch (Exception e) {
                statuses.add(500);
            }
        };

        executor.submit(refundTask);
        executor.submit(refundTask);
        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        long okCount = statuses.stream().filter(s -> s == 200).count();
        long conflictCount = statuses.stream().filter(s -> s == 409).count();
        assertEquals(1, okCount, "Un seul remboursement concurrent doit réussir (200 OK)");
        assertEquals(1, conflictCount, "Le second remboursement concurrent doit être rejeté (409 Conflict)");
        assertEquals(1, waveClientMock.getRefundCallCount(), "waveClient.initiateEscrowRefund(...) doit être appelé exactement 1 fois");

        TransactionEntity reloaded = transactionRepository.findById(txBloquee.getId()).orElseThrow();
        assertEquals(StatutSequestre.REMBOURSE, reloaded.getStatutSequestre());
        assertEquals(paidWaveRef, reloaded.getReferenceWave());
        assertTrue(reloaded.getReferenceRemboursementWave() != null && reloaded.getReferenceRemboursementWave().startsWith("WAVE-REFUND-SANDBOX-"));
    }

    @Test
    @DisplayName("LOT 2 : Scénario complet A (BLOQUE, actif) + B (INITIALISE, inactif) -> remboursement A -> création C -> refus paiement/signature/remboursement B (409) avec 0 appel Wave")
    void testScenarioCompletDossierInactifHistoriqueApresRemboursement() throws Exception {
        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().minusDays(2))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(tiers)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        // 1. Dossier historique B : INITIALISE, dossier_actif = FALSE
        TransactionEntity txB = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.FALSE)
                .build());

        // 2. Dossier actif A : créé puis payé -> BLOQUE, dossier_actif = TRUE, annonce RESERVEE
        TransactionEntity txA = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        mockMvc.perform(post("/api/v1/transactions/" + txA.getId() + "/paiement-caution")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        annonce = annonceRepository.findById(annonce.getId()).orElseThrow();
        assertEquals(StatutPublication.RESERVEE, annonce.getStatutPublication());

        // 3. Remboursement de A par l'ADMIN -> A passe à REMBOURSE et l'annonce repasse à DISPONIBLE
        mockMvc.perform(post("/api/v1/transactions/" + txA.getId() + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Annulation dossier A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("REMBOURSE")));

        AnnonceEntity annonceAfterRefund = annonceRepository.findById(annonce.getId()).orElseThrow();
        assertEquals(StatutPublication.DISPONIBLE, annonceAfterRefund.getStatutPublication());

        // 4. Création d'une nouvelle transaction C par un nouvel utilisateur (tiers) -> 201 Created
        MvcResult resC = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tiers.tx.test@ecourtier.sn").roles("LOCATAIRE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andReturn();
        String txCId = objectMapper.readTree(resC.getResponse().getContentAsString()).get("id").asText();

        // 5. Remise à zéro des compteurs Wave avant de tester les opérations interdites sur B
        waveClientMock.resetCounters();

        // 6. Tentative de paiement de B (dossier_actif = FALSE) -> 409 Conflict et 0 appel Wave
        mockMvc.perform(post("/api/v1/transactions/" + txB.getId() + "/paiement-caution")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isConflict());
        assertEquals(0, waveClientMock.getCheckoutCallCount(), "Aucun appel Wave ne doit avoir lieu pour un dossier inactif");

        // 7. Tentative de signature de B (dossier_actif = FALSE) -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/" + txB.getId() + "/bail/signer")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("loc.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isConflict());

        // 8. Tentative de remboursement de B (dossier_actif = FALSE) -> 409 Conflict et 0 appel Wave
        mockMvc.perform(post("/api/v1/transactions/" + txB.getId() + "/remboursement")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin.tx.test@ecourtier.sn").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Tentative sur B inactif\"}"))
                .andExpect(status().isConflict());
        assertEquals(0, waveClientMock.getRefundCallCount(), "Aucun appel de remboursement Wave ne doit avoir lieu pour un dossier inactif");

        TransactionEntity reloadedB = transactionRepository.findById(txB.getId()).orElseThrow();
        assertEquals(StatutSequestre.INITIALISE, reloadedB.getStatutSequestre());
        org.junit.jupiter.api.Assertions.assertFalse(reloadedB.getDossierActif());
        org.junit.jupiter.api.Assertions.assertNull(reloadedB.getReferenceWave());

        // 9. La transaction active C continue de fonctionner normalement (paiement caution -> 200 OK, 1 appel Wave)
        mockMvc.perform(post("/api/v1/transactions/" + txCId + "/paiement-caution")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("tiers.tx.test@ecourtier.sn").roles("LOCATAIRE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));
        assertEquals(1, waveClientMock.getCheckoutCallCount(), "Le paiement de la transaction active C doit déclencher exactement 1 appel Wave");
    }
}
