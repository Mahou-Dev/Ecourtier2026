package com.ecourtier.domain.transaction.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveCheckoutResponse;
import com.ecourtier.domain.transaction.client.WaveClient;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.mapper.TransactionMapper;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.WavePaymentReconciliationService;
import com.ecourtier.domain.transaction.service.WaveWebhookService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WavePaymentRobustnessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PaiementWaveRepository paiementWaveRepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.MessageIARepository messageIARepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.ConversationIARepository conversationIARepository;

    @Autowired
    private WaveWebhookService waveWebhookService;

    @Autowired
    private WavePaymentReconciliationService reconciliationService;

    @MockitoSpyBean
    private WaveClient waveClient;

    @MockitoSpyBean
    private TransactionMapper transactionMapper;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private String tokenLocataire;

    @BeforeEach
    void setUp() {
        Mockito.reset(waveClient, transactionMapper);
        testDatabaseCleaner.cleanAll();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ba")
                .prenom("Moussa")
                .email("prop.wave@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221773330001")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Kane")
                .prenom("Fatou")
                .email("loc.wave@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass1234!"))
                .telephone("+221773330002")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        tokenLocataire = jwtService.generateAccessToken(locataire);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(waveClient, transactionMapper);
        testDatabaseCleaner.cleanAll();
    }

    private AnnonceEntity createAnnonce(StatutPublication statut) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Wave RES-02")
                .description("Test paiement découplé et webhooks")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(75.0)
                .nombrePieces(3)
                .quartier("Sacré-Coeur")
                .ville("Dakar")
                .latitude(14.71)
                .longitude(-17.46)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(statut)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    private TransactionEntity createTransaction(AnnonceEntity annonce, StatutSequestre statut, LocalDateTime expiration) {
        boolean actif = statut == StatutSequestre.INITIALISE || statut == StatutSequestre.BLOQUE || statut == StatutSequestre.LIBERE;
        return transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(statut)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(actif)
                .dateExpiration(expiration)
                .build());
    }

    @Test
    @DisplayName("1. RES-02 : Paiement confirmé hors transaction SQL (aucun verrou SQL pendant l'appel WaveClient)")
    void testPaiementConfirmeSansVerrouSqlPendantAppelWave() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        AtomicBoolean txActiveDuringCheckout = new AtomicBoolean(true);
        AtomicBoolean txActiveDuringVerify = new AtomicBoolean(true);

        doAnswer(invocation -> {
            txActiveDuringCheckout.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(waveClient).initiateEscrowCheckout(eq(tx.getId()), any(), any());

        doAnswer(invocation -> {
            txActiveDuringVerify.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(waveClient).verifyPaymentStatus(any(), eq(tx.getId()), any());

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        assertFalse(txActiveDuringCheckout.get(), "Aucune transaction SQL active ne doit être ouverte durant initiateEscrowCheckout");
        assertFalse(txActiveDuringVerify.get(), "Aucune transaction SQL active ne doit être ouverte durant verifyPaymentStatus");

        List<PaiementWaveEntity> logs = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId());
        assertEquals(1, logs.size());
        assertEquals(StatutPaiementWave.CONFIRME, logs.get(0).getStatut());
        assertNotNull(logs.get(0).getReferenceExterne());
        assertNotNull(logs.get(0).getDateConfirmation());
    }

    @Test
    @DisplayName("2. RES-02 : Paiement refusé par Wave -> 502 + ECHOUE, nouvelle tentative autorisée ensuite -> 200")
    void testPaiementRefusePuisNouvelleTentativeReussie() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        doThrow(new RuntimeException("Wave 500 temporaire"))
                .doCallRealMethod()
                .when(waveClient).initiateEscrowCheckout(eq(tx.getId()), any(), any());

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isBadGateway());

        TransactionEntity stillInit = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.INITIALISE, stillInit.getStatutSequestre());
        assertEquals(StatutPaiementWave.ECHOUE, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0).getStatut());

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));
    }

    @Test
    @DisplayName("3. RES-02 : Notification webhook dupliquée et double paiement REST idempotents")
    void testWebhookDupliqueEtDoublePaiementIdempotents() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        String payload = String.format(
                "{\"eventId\":\"evt-1\",\"type\":\"checkout.session.completed\",\"status\":\"SUCCESS\",\"transactionId\":\"%s\",\"waveReference\":\"WAVE-WH-DUP-001\",\"amount\":505000,\"currency\":\"XOF\"}",
                tx.getId()
        );
        String sig = waveWebhookService.computeHmacSha256Hex(payload);

        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("CONFIRME")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")))
                .andExpect(jsonPath("$.latePayment", is(false)));

        // Rejeu identique du webhook -> 200 OK idempotent, pas de doublon en base
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", "sha256=" + sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("CONFIRME")))
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        assertEquals(1, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).size());

        // Tentative de paiement REST après confirmation webhook -> 409 sans appel Wave
        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isConflict());
        verify(waveClient, times(0)).initiateEscrowCheckout(any(), any(), any());
    }

    @Test
    @DisplayName("4. RES-02 : Notification tardive sur transaction EXPIRE ne débloque pas la transaction et marque TARDIF_A_REMBOURSER")
    void testWebhookTardifSurTransactionExpiree() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity expiredTx = createTransaction(annonce, StatutSequestre.EXPIRE, LocalDateTime.now().minusHours(2));

        String payload = String.format(
                "{\"eventId\":\"evt-late-1\",\"type\":\"checkout.session.completed\",\"status\":\"SUCCESS\",\"transactionId\":\"%s\",\"waveReference\":\"WAVE-WH-LATE-001\",\"amount\":505000}",
                expiredTx.getId()
        );
        String sig = waveWebhookService.computeHmacSha256Hex(payload);

        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("TARDIF_A_REMBOURSER")))
                .andExpect(jsonPath("$.statutSequestre", is("EXPIRE")))
                .andExpect(jsonPath("$.latePayment", is(true)));

        // Deuxième webhook tardif dupliqué -> reste idempotent
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("TARDIF_A_REMBOURSER")));

        assertEquals(StatutSequestre.EXPIRE, transactionRepository.findById(expiredTx.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutPublication.DISPONIBLE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
        List<PaiementWaveEntity> rows = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(expiredTx.getId());
        assertEquals(1, rows.size());
        assertEquals(StatutPaiementWave.TARDIF_A_REMBOURSER, rows.get(0).getStatut());
    }

    @Test
    @DisplayName("5. RES-02 : Notification tardive reçue alors qu'une autre transaction active est BLOQUE sur l'annonce")
    void testWebhookTardifAlorsQuUneAutreTransactionEstActive() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.RESERVEE);
        TransactionEntity cancelledTx = createTransaction(annonce, StatutSequestre.ANNULE, LocalDateTime.now().plusHours(10));
        TransactionEntity activeTx = createTransaction(annonce, StatutSequestre.BLOQUE, LocalDateTime.now().plusHours(20));

        String payload = String.format(
                "{\"eventId\":\"evt-late-2\",\"type\":\"checkout.session.completed\",\"status\":\"SUCCESS\",\"transactionId\":\"%s\",\"waveReference\":\"WAVE-WH-LATE-002\",\"amount\":505000}",
                cancelledTx.getId()
        );
        String sig = waveWebhookService.computeHmacSha256Hex(payload);

        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("TARDIF_A_REMBOURSER")))
                .andExpect(jsonPath("$.statutSequestre", is("ANNULE")))
                .andExpect(jsonPath("$.latePayment", is(true)));

        assertEquals(StatutSequestre.ANNULE, transactionRepository.findById(cancelledTx.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutSequestre.BLOQUE, transactionRepository.findById(activeTx.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutPublication.RESERVEE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
    }

    @Test
    @DisplayName("6. RES-02 : Réconciliation rembourse automatiquement un paiement tardif Wave (TARDIF_A_REMBOURSER -> RECONCILIE)")
    void testReconciliationRemboursementAutomatiquePaiementTardif() {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity expiredTx = createTransaction(annonce, StatutSequestre.EXPIRE, LocalDateTime.now().minusMinutes(5));

        WaveCheckoutResponse checkout = waveClient.initiateEscrowCheckout(expiredTx.getId(), BigDecimal.valueOf(505000), "+221773330002");
        paiementWaveRepository.saveAndFlush(PaiementWaveEntity.builder()
                .transaction(expiredTx)
                .typeOperation(TypeOperationWave.PAIEMENT_SEQUESTRE)
                .statut(StatutPaiementWave.TARDIF_A_REMBOURSER)
                .cleIdempotence("TARDIF-" + expiredTx.getId())
                .referenceExterne(checkout.getWaveReference())
                .montant(BigDecimal.valueOf(505000))
                .devise("XOF")
                .messageErreur("Paiement tardif simulé")
                .build());

        int count = reconciliationService.reconcilierPaiementsEnAttente();
        assertEquals(1, count);

        PaiementWaveEntity reconciled = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(expiredTx.getId()).get(0);
        assertEquals(StatutPaiementWave.RECONCILIE, reconciled.getStatut());
        assertEquals(StatutSequestre.EXPIRE, transactionRepository.findById(expiredTx.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutPublication.DISPONIBLE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
    }

    @Test
    @DisplayName("7. RES-02 : Reprise après redémarrage/crash pendant EN_COURS avec référence Wave déjà émise")
    void testRepriseApresCrashPendantTraitement() {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        // Simule l'état laissé en base juste après initiateEscrowCheckout avant un redémarrage JVM
        WaveCheckoutResponse checkout = waveClient.initiateEscrowCheckout(tx.getId(), BigDecimal.valueOf(505000), "+221773330002");
        paiementWaveRepository.saveAndFlush(PaiementWaveEntity.builder()
                .transaction(tx)
                .typeOperation(TypeOperationWave.PAIEMENT_SEQUESTRE)
                .statut(StatutPaiementWave.EN_COURS)
                .cleIdempotence("CRASH-" + tx.getId())
                .referenceExterne(checkout.getWaveReference())
                .montant(BigDecimal.valueOf(505000))
                .devise("XOF")
                .dateModification(LocalDateTime.now().minusMinutes(5))
                .build());

        int count = reconciliationService.reconcilierPaiementsEnAttente(Duration.ofMinutes(1));
        assertEquals(1, count);

        TransactionEntity recovered = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.BLOQUE, recovered.getStatutSequestre());
        assertEquals(checkout.getWaveReference(), recovered.getReferenceWave());
        assertEquals(StatutPublication.RESERVEE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
        assertEquals(StatutPaiementWave.CONFIRME, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0).getStatut());
    }

    @Test
    @DisplayName("8. RES-02 : Échec SQL post-confirmation Wave sauvegarde A_RECONCILIER en REQUIRES_NEW puis réconcilie")
    void testEchecSqlPostConfirmationPuisReconciliation() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        doThrow(new DataRetrievalFailureException("Panne SQL simulée en Phase 3"))
                .when(transactionMapper).toResponseDTO(any());

        mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isServiceUnavailable());

        // Phase 3 ayant échoué, la transaction reste INITIALISE mais l'outbox garde A_RECONCILIER + referenceExterne
        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());
        PaiementWaveEntity outbox = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0);
        assertEquals(StatutPaiementWave.A_RECONCILIER, outbox.getStatut());
        assertNotNull(outbox.getReferenceExterne());

        // Rétablissement de la persistance puis exécution de la réconciliation -> BLOQUE + CONFIRME
        Mockito.reset(transactionMapper);
        int count = reconciliationService.reconcilierPaiementsEnAttente();
        assertEquals(1, count);

        TransactionEntity reconciledTx = transactionRepository.findById(tx.getId()).orElseThrow();
        assertEquals(StatutSequestre.BLOQUE, reconciledTx.getStatutSequestre());
        assertEquals(outbox.getReferenceExterne(), reconciledTx.getReferenceWave());
        assertEquals(StatutPaiementWave.CONFIRME, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0).getStatut());
    }

    @Test
    @DisplayName("9. RES-02 Concurrence : Annulation immédiate possible pendant l'appel Wave + paiement tardif/concurrent maîtrisé")
    void testConcurrenceAnnulationPendantAppelWaveEtDoubleInitiation() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        CountDownLatch inWaveCall = new CountDownLatch(1);
        CountDownLatch releaseWaveCall = new CountDownLatch(1);

        doAnswer(invocation -> {
            inWaveCall.countDown();
            releaseWaveCall.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(waveClient).initiateEscrowCheckout(eq(tx.getId()), any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> paymentStatus = Collections.synchronizedList(new ArrayList<>());
        List<Integer> duplicateAttemptStatus = Collections.synchronizedList(new ArrayList<>());

        pool.submit(() -> {
            try {
                paymentStatus.add(mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                                .header("Authorization", "Bearer " + tokenLocataire))
                        .andReturn().getResponse().getStatus());
            } catch (Exception e) {
                paymentStatus.add(500);
            }
        });

        assertTrue(inWaveCall.await(5, TimeUnit.SECONDS), "Le premier appel Wave doit avoir démarré");

        // Un deuxième appel paiement concurrent pendant que le 1er attend Wave -> rejeté immédiatement 409
        duplicateAttemptStatus.add(mockMvc.perform(post("/api/v1/transactions/{id}/paiement-caution", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andReturn().getResponse().getStatus());
        assertEquals(409, duplicateAttemptStatus.get(0));

        // Pendant que Thread A est bloqué hors verrou SQL chez Wave, l'annulation acquiert immédiatement Annonce -> Transaction!
        mockMvc.perform(post("/api/v1/transactions/{id}/annuler", tx.getId())
                        .header("Authorization", "Bearer " + tokenLocataire))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("ANNULE")));

        // Laisser Wave répondre après l'annulation -> Phase 3 détecte ANNULE, marque TARDIF_A_REMBOURSER et renvoie 409
        releaseWaveCall.countDown();
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(409, paymentStatus.get(0));
        assertEquals(StatutSequestre.ANNULE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutPublication.DISPONIBLE, annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());
        PaiementWaveEntity recorded = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0);
        assertEquals(StatutPaiementWave.TARDIF_A_REMBOURSER, recorded.getStatut());

        // La réconciliation rembourse ce paiement tardif -> RECONCILIE
        assertEquals(1, reconciliationService.reconcilierPaiementsEnAttente());
        assertEquals(StatutPaiementWave.RECONCILIE, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0).getStatut());
    }

    @Test
    @DisplayName("10. RES-02 Sécurité : Webhook Wave sans signature ou signature HMAC invalide -> 401 ; échec checkout.session.failed -> ECHOUE")
    void testWebhookHmacInvalideRefuse401EtFailureWebhook() throws Exception {
        AnnonceEntity annonce = createAnnonce(StatutPublication.DISPONIBLE);
        TransactionEntity tx = createTransaction(annonce, StatutSequestre.INITIALISE, LocalDateTime.now().plusHours(24));

        String payload = String.format(
                "{\"eventId\":\"evt-sec\",\"type\":\"checkout.session.completed\",\"status\":\"SUCCESS\",\"transactionId\":\"%s\",\"waveReference\":\"WAVE-SEC-001\",\"amount\":505000}",
                tx.getId()
        );

        // Sans header signature -> 401
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        // Signature falsifiée -> 401
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", "0000000000000000000000000000000000000000000000000000000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        // Payload altéré avec signature de l'ancien payload -> 401
        String sig = waveWebhookService.computeHmacSha256Hex(payload);
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("505000", "1")))
                .andExpect(status().isUnauthorized());

        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());

        // Notification d'échec signée -> ECHOUE et transaction reste INITIALISE
        String failedPayload = String.format(
                "{\"eventId\":\"evt-fail\",\"type\":\"checkout.session.failed\",\"status\":\"FAILED\",\"transactionId\":\"%s\",\"waveReference\":\"WAVE-FAIL-001\",\"amount\":505000,\"failureReason\":\"Solde insuffisant\"}",
                tx.getId()
        );
        mockMvc.perform(post("/api/v1/webhooks/wave")
                        .header("X-Wave-Signature", waveWebhookService.computeHmacSha256Hex(failedPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPaiement", is("ECHOUE")));

        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(tx.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutPaiementWave.ECHOUE, paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId()).get(0).getStatut());
    }
}
