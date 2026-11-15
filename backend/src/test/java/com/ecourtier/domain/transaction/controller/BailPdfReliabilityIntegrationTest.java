package com.ecourtier.domain.transaction.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutDocumentBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.BailPdfRecoveryScheduler;
import com.ecourtier.domain.transaction.service.PdfBailGeneratorService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BailPdfReliabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private BailPdfRecoveryScheduler bailPdfRecoveryScheduler;

    @MockitoSpyBean
    private PdfBailGeneratorService pdfBailGeneratorService;

    private final List<UUID> createdTxIds = new ArrayList<>();
    private final List<UUID> createdAnnonceIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    private UtilisateurEntity proprio;
    private UtilisateurEntity locataire;
    private UtilisateurEntity tiers;
    private String tokenProprio;
    private String tokenLocataire;
    private String tokenTiers;

    @BeforeEach
    void setUp() {
        reset(pdfBailGeneratorService);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String phoneSeed = String.valueOf(Math.abs(suffix.hashCode() % 10000000));

        proprio = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ba").prenom("Mamadou").email("proprio.pdf." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221771" + phoneSeed)
                .role(Role.PROPRIETAIRE).actif(true).build());
        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sarr").prenom("Fatou").email("loc.pdf." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221772" + phoneSeed)
                .role(Role.LOCATAIRE).actif(true).build());
        tiers = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall").prenom("Tiers").email("tiers.pdf." + suffix + "@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221773" + phoneSeed)
                .role(Role.LOCATAIRE).actif(true).build());

        createdUserIds.addAll(List.of(proprio.getId(), locataire.getId(), tiers.getId()));
        tokenProprio = jwtService.generateAccessToken(proprio);
        tokenLocataire = jwtService.generateAccessToken(locataire);
        tokenTiers = jwtService.generateAccessToken(tiers);
    }

    @AfterEach
    void tearDown() throws Exception {
        reset(pdfBailGeneratorService);
        Path dir = Paths.get("uploads/baux/");
        for (UUID txId : createdTxIds) {
            transactionRepository.findById(txId).ifPresent(tx -> {
                if (tx.getPdfBailPath() != null) {
                    try {
                        Files.deleteIfExists(Path.of(tx.getPdfBailPath()));
                    } catch (Exception ignored) {
                    }
                }
                transactionRepository.delete(tx);
            });
            if (Files.exists(dir)) {
                Files.deleteIfExists(dir.resolve("bail_" + txId + ".pdf"));
            }
        }
        for (UUID annonceId : createdAnnonceIds) {
            annonceRepository.findById(annonceId).ifPresent(annonceRepository::delete);
        }
        for (UUID userId : createdUserIds) {
            utilisateurRepository.findById(userId).ifPresent(utilisateurRepository::delete);
        }
        createdTxIds.clear();
        createdAnnonceIds.clear();
        createdUserIds.clear();
    }

    private AnnonceEntity createAnnonce(StatutPublication statut) {
        AnnonceEntity annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Almadies PDF")
                .description("Villa pour test PDF fiable")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(500000))
                .superficie(220.0)
                .nombrePieces(5)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.74)
                .longitude(-17.51)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(statut)
                .supprime(false)
                .utilisateur(proprio)
                .build());
        createdAnnonceIds.add(annonce.getId());
        return annonce;
    }

    private TransactionEntity createTransaction(AnnonceEntity annonce, StatutSequestre sequestre, StatutBail bail) {
        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprio)
                .montantCaution(BigDecimal.valueOf(500000))
                .montantPremierLoyer(BigDecimal.valueOf(500000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(sequestre)
                .statutBail(bail)
                .dossierActif(Boolean.TRUE)
                .build());
        createdTxIds.add(tx.getId());
        return tx;
    }

    @Test
    @DisplayName("Phase 3 C: Échec PDF lors double signature préserve LIBERE+SIGNE_COMPLET, trace ECHEC, puis récupération manuelle 503->200 et idempotence")
    void testFailureOnReleaseManualRetryWithNoRollbackLossAndIdempotency() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.BLOQUE, StatutBail.NON_SIGNE);

        // 1. Le locataire signe -> encore BLOQUE / SIGNE_LOCATAIRE / NON_GENERE
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutDocumentBail").value("NON_GENERE"));

        // Tentative de génération manuelle avant libération -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/pdf/generer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isConflict());

        // 2. Simuler échec disque/I/O lors de la 2e signature
        doThrow(new RuntimeException("Disque saturé simulé")).when(pdfBailGeneratorService).generatePdfBail(any());

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre").value("LIBERE"))
                .andExpect(jsonPath("$.statutBail").value("SIGNE_COMPLET"))
                .andExpect(jsonPath("$.statutDocumentBail").value("ECHEC"))
                .andExpect(jsonPath("$.pdfBailTentatives").value(1));

        TransactionEntity persistedAfterFail = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.LIBERE, persistedAfterFail.getStatutSequestre());
        assertEquals(StatutBail.SIGNE_COMPLET, persistedAfterFail.getStatutBail());
        assertEquals(StatutDocumentBail.ECHEC, persistedAfterFail.getStatutDocumentBail());
        assertEquals(1, persistedAfterFail.getPdfBailTentatives());
        assertTrue(persistedAfterFail.getPdfBailErreur().contains("Disque saturé simulé"));
        assertNotNull(persistedAfterFail.getPdfBailDerniereTentative());
        assertNull(persistedAfterFail.getPdfBailPath());
        assertFalse(Files.exists(Paths.get("uploads/baux/bail_" + tx.getId() + ".pdf")));
        assertEquals(StatutPublication.LOUEE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());

        // 3. Relance manuelle alors que la panne persiste -> 503 MAIS tentative=2 bien commitée en base !
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/pdf/generer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprio))
                .andExpect(status().isServiceUnavailable());

        TransactionEntity persistedAfterSecondFail = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutDocumentBail.ECHEC, persistedAfterSecondFail.getStatutDocumentBail());
        assertEquals(2, persistedAfterSecondFail.getPdfBailTentatives());

        // 4. Tiers non autorisé rejeté en 403
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/pdf/generer", tx.getId())
                        .header("Authorization", "Bearer " + tokenTiers))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transactions/{id}/bail/pdf", tx.getId())
                        .header("Authorization", "Bearer " + tokenTiers))
                .andExpect(status().isForbidden());

        // 5. Restauration du générateur -> régénération manuelle réussit -> 200 GENERE + fichier réel
        doCallRealMethod().when(pdfBailGeneratorService).generatePdfBail(any());
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/pdf/generer", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutDocumentBail").value("GENERE"))
                .andExpect(jsonPath("$.pdfBailTentatives").value(3))
                .andExpect(jsonPath("$.pdfBailErreur").doesNotExist());

        TransactionEntity recovered = transactionRepository.findById(tx.getId()).orElseThrow();
        assertNotNull(recovered.getPdfBailPath());
        Path pdfFile = Path.of(recovered.getPdfBailPath());
        assertTrue(Files.isRegularFile(pdfFile) && Files.size(pdfFile) > 0);

        // 6. Idempotence : un second appel ne régénère pas inutilement et n'incrémente pas les tentatives
        mockMvc.perform(post("/api/v1/transactions/{id}/bail/pdf/generer", tx.getId())
                        .header("Authorization", "Bearer " + tokenProprio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutDocumentBail").value("GENERE"))
                .andExpect(jsonPath("$.pdfBailTentatives").value(3));

        // 3 tentatives d'appel au générateur au total (1 libération + 1 échec manuel + 1 succès manuel, 0 pour l'appel idempotent)
        verify(pdfBailGeneratorService, times(3)).generatePdfBail(any());

        // 7. Téléchargement GET /bail/pdf retourne un PDF réel
        MvcResult pdfDownload = mockMvc.perform(get("/api/v1/transactions/{id}/bail/pdf", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        byte[] bytes = pdfDownload.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 100);
        assertEquals("%PDF", new String(bytes, 0, 4));
    }

    @Test
    @DisplayName("Phase 3 C: Reprise automatique planifiée des dossiers ECHEC/A_GENERER sans dépasser maxTentatives")
    void testScheduledPdfRecoveryPicksUpFailedAndPendingOnlyUnderMaxAttempts() throws Exception {
        AnnonceEntity annonceEligible = createAnnonce(StatutPublication.LOUEE);
        TransactionEntity eligible = createTransaction(annonceEligible, StatutSequestre.LIBERE, StatutBail.SIGNE_COMPLET);
        eligible.setDateLiberation(LocalDateTime.now().minusMinutes(5));
        eligible.setDateSignatureLocataire(LocalDateTime.now().minusMinutes(6));
        eligible.setDateSignatureProprietaire(LocalDateTime.now().minusMinutes(5));
        eligible.setStatutDocumentBail(StatutDocumentBail.ECHEC);
        eligible.setPdfBailTentatives(1);
        eligible.setPdfBailErreur("Erreur réseau transitoire");
        eligible = transactionRepository.save(eligible);

        AnnonceEntity annonceExhausted = createAnnonce(StatutPublication.LOUEE);
        TransactionEntity exhausted = createTransaction(annonceExhausted, StatutSequestre.LIBERE, StatutBail.SIGNE_COMPLET);
        exhausted.setDateLiberation(LocalDateTime.now().minusMinutes(5));
        exhausted.setDateSignatureLocataire(LocalDateTime.now().minusMinutes(6));
        exhausted.setDateSignatureProprietaire(LocalDateTime.now().minusMinutes(5));
        exhausted.setStatutDocumentBail(StatutDocumentBail.ECHEC);
        exhausted.setPdfBailTentatives(5); // >= maxTentatives par défaut (5)
        exhausted.setPdfBailErreur("Échec permanent");
        exhausted = transactionRepository.save(exhausted);

        bailPdfRecoveryScheduler.reprendrePdfEnEchec();

        TransactionEntity afterEligible = transactionRepository.findById(eligible.getId()).orElseThrow();
        assertEquals(StatutDocumentBail.GENERE, afterEligible.getStatutDocumentBail());
        assertEquals(2, afterEligible.getPdfBailTentatives());
        assertNull(afterEligible.getPdfBailErreur());
        assertNotNull(afterEligible.getPdfBailPath());
        assertTrue(Files.size(Path.of(afterEligible.getPdfBailPath())) > 0);

        TransactionEntity afterExhausted = transactionRepository.findById(exhausted.getId()).orElseThrow();
        assertEquals(StatutDocumentBail.ECHEC, afterExhausted.getStatutDocumentBail());
        assertEquals(5, afterExhausted.getPdfBailTentatives());
    }

    @Test
    @DisplayName("Phase 3 C: Signature refusée avant paiement Wave et statutDocumentBail reste NON_GENERE")
    void testSigningBeforePaymentRejectedAndPdfRemainsNonGenere() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity txInitialise = createTransaction(annonce, StatutSequestre.INITIALISE, StatutBail.NON_SIGNE);

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txInitialise.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/transactions/{id}/bail/signer", txInitialise.getId())
                        .header("Authorization", "Bearer " + tokenProprio))
                .andExpect(status().isConflict());

        TransactionEntity reloaded = transactionRepository.findById(txInitialise.getId()).orElseThrow();
        assertEquals(StatutSequestre.INITIALISE, reloaded.getStatutSequestre());
        assertEquals(StatutBail.NON_SIGNE, reloaded.getStatutBail());
        assertEquals(StatutDocumentBail.NON_GENERE, reloaded.getStatutDocumentBail());
        assertNull(reloaded.getPdfBailPath());
        verify(pdfBailGeneratorService, times(0)).generatePdfBail(any());
    }
}
