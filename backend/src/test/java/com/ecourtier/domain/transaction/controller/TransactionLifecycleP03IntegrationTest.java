package com.ecourtier.domain.transaction.controller;

import com.ecourtier.common.security.JwtService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.transaction.client.WaveClientMock;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.TransactionExpirationScheduler;
import com.ecourtier.domain.transaction.service.TransactionService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionLifecycleP03IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private TransactionService transactionService;

    @Autowired
    private TransactionExpirationScheduler transactionExpirationScheduler;

    @Autowired
    private WaveClientMock waveClientMock;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire1;
    private UtilisateurEntity locataire2;
    private UtilisateurEntity admin;

    private String tokenProprietaire;
    private String tokenLocataire1;
    private String tokenLocataire2;
    private String tokenAdmin;

    private AnnonceEntity annonce;

    @BeforeEach
    void setUp() {
        cleanTables();
        waveClientMock.resetState();

        proprietaire = createUser("p03.prop@ecourtier.sn", Role.PROPRIETAIRE);
        locataire1 = createUser("p03.loc1@ecourtier.sn", Role.LOCATAIRE);
        locataire2 = createUser("p03.loc2@ecourtier.sn", Role.LOCATAIRE);
        admin = createUser("p03.admin@ecourtier.sn", Role.ADMIN);

        tokenProprietaire = jwtService.generateAccessToken(proprietaire);
        tokenLocataire1 = jwtService.generateAccessToken(locataire1);
        tokenLocataire2 = jwtService.generateAccessToken(locataire2);
        tokenAdmin = jwtService.generateAccessToken(admin);

        annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Mermoz P03")
                .description("Villa pour tests de cycle de vie transactionnel")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(400000))
                .superficie(220.0)
                .nombrePieces(5)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());

        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire1)
                .dateHeure(LocalDateTime.now().minusDays(2))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire2)
                .dateHeure(LocalDateTime.now().minusDays(1))
                .statut(StatutRendezVous.EFFECTUE)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanTables();
    }

    private void cleanTables() {
        testDatabaseCleaner.cleanAll();
    }

    private UtilisateurEntity createUser(String email, Role role) {
        return utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("User")
                .prenom(role.name())
                .email(email)
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221779998877")
                .role(role)
                .actif(true)
                .build());
    }

    @Test
    @DisplayName("P0-3 : Annulation d'une transaction INITIALISE (locataire/admin), contrôle d'accès et déblocage de l'annonce")
    void testAnnulationTransactionInitialiseEtDeblocage() throws Exception {
        // 1. Initiation -> dateExpiration positionnée et dossierActif = true
        MvcResult initResult = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenLocataire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andExpect(jsonPath("$.dossierActif", is(true)))
                .andExpect(jsonPath("$.dateExpiration", notNullValue()))
                .andReturn();

        UUID txId = UUID.fromString(objectMapper.readTree(initResult.getResponse().getContentAsString()).get("id").asText());

        // 2. Un tiers locataire ou le propriétaire ne peuvent pas annuler -> 403 Forbidden
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/annuler")
                        .header("Authorization", "Bearer " + tokenLocataire2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Tentative tier\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/transactions/" + txId + "/annuler")
                        .header("Authorization", "Bearer " + tokenProprietaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Tentative propriétaire\"}"))
                .andExpect(status().isForbidden());

        // 3. Annulation légitime par locataire1 -> 200 OK, ANNULE, dossierActif = false
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/annuler")
                        .header("Authorization", "Bearer " + tokenLocataire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Changement d'avis du locataire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("ANNULE")))
                .andExpect(jsonPath("$.dossierActif", is(false)))
                .andExpect(jsonPath("$.dateCloture", notNullValue()))
                .andExpect(jsonPath("$.motifCloture", is("Changement d'avis du locataire")));

        // 4. Opérations ultérieures interdites sur la transaction annulée (409 Conflict) + 0 appel Wave
        mockMvc.perform(post("/api/v1/transactions/" + txId + "/annuler")
                        .header("Authorization", "Bearer " + tokenLocataire1))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/transactions/" + txId + "/paiement-caution")
                        .header("Authorization", "Bearer " + tokenLocataire1))
                .andExpect(status().isConflict());
        assertEquals(0, waveClientMock.getCheckoutCallCount());

        mockMvc.perform(post("/api/v1/transactions/" + txId + "/bail/signer")
                        .header("Authorization", "Bearer " + tokenLocataire1))
                .andExpect(status().isConflict());

        // 5. L'annonce est immédiatement débloquée pour un second candidat (locataire2) -> 201 Created
        MvcResult secondInit = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenLocataire2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andReturn();
        UUID secondTxId = UUID.fromString(objectMapper.readTree(secondInit.getResponse().getContentAsString()).get("id").asText());

        // 6. Annulation également autorisée pour ADMIN avant paiement -> 200 OK
        mockMvc.perform(post("/api/v1/transactions/" + secondTxId + "/annuler")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\": \"Nettoyage administratif\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("ANNULE")))
                .andExpect(jsonPath("$.dossierActif", is(false)));

        // 7. Nouvelle initiation + paiement -> tentative d'annulation sur BLOQUE refusée (409)
        MvcResult thirdInit = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenLocataire1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID thirdTxId = UUID.fromString(objectMapper.readTree(thirdInit.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/transactions/" + thirdTxId + "/paiement-caution")
                        .header("Authorization", "Bearer " + tokenLocataire1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutSequestre", is("BLOQUE")));

        mockMvc.perform(post("/api/v1/transactions/" + thirdTxId + "/annuler")
                        .header("Authorization", "Bearer " + tokenLocataire1))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/transactions/" + thirdTxId + "/annuler")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("P0-3 : Expiration TTL persistée lors d'un paiement/signature tardif sans rollback et libération pour nouveau dossier")
    void testExpirationTtlPaiementTardifEtNettoyageParesseux() throws Exception {
        // 1. Transaction INITIALISE expirée (échéance dans le passé)
        TransactionEntity expiredTx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire1)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(400000))
                .montantPremierLoyer(BigDecimal.valueOf(400000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .dateCreation(LocalDateTime.now().minusHours(26))
                .dateExpiration(LocalDateTime.now().minusHours(2))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        // 2. Tentative de paiement tardif -> 409 Conflict, 0 appel Wave, ET persistance EXPIRE (aucun rollback)
        mockMvc.perform(post("/api/v1/transactions/" + expiredTx.getId() + "/paiement-caution")
                        .header("Authorization", "Bearer " + tokenLocataire1))
                .andExpect(status().isConflict());
        assertEquals(0, waveClientMock.getCheckoutCallCount(), "Aucun appel Wave ne doit être effectué sur une transaction expirée");

        TransactionEntity reloadedAfterLatePay = transactionRepository.findById(expiredTx.getId()).orElseThrow();
        assertEquals(StatutSequestre.EXPIRE, reloadedAfterLatePay.getStatutSequestre());
        assertFalse(reloadedAfterLatePay.getDossierActif());
        assertNotNull(reloadedAfterLatePay.getDateCloture());
        assertNull(reloadedAfterLatePay.getReferenceWave());

        // 3. Un autre candidat peut immédiatement initier une nouvelle transaction
        MvcResult newTxRes = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenLocataire2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID newTxId = UUID.fromString(objectMapper.readTree(newTxRes.getResponse().getContentAsString()).get("id").asText());

        // Simuler expiration de la deuxième transaction puis tentative de signature tardive -> 409 + EXPIRE persisté
        TransactionEntity secondTx = transactionRepository.findById(newTxId).orElseThrow();
        secondTx.setDateExpiration(LocalDateTime.now().minusMinutes(1));
        transactionRepository.save(secondTx);

        mockMvc.perform(post("/api/v1/transactions/" + newTxId + "/bail/signer")
                        .header("Authorization", "Bearer " + tokenLocataire2))
                .andExpect(status().isConflict());

        TransactionEntity secondReloaded = transactionRepository.findById(newTxId).orElseThrow();
        assertEquals(StatutSequestre.EXPIRE, secondReloaded.getStatutSequestre());
        assertFalse(secondReloaded.getDossierActif());

        // 4. Lazy cleanup lors de POST /annonces/{id}/transactions si une tx expirée traîne encore en base
        TransactionEntity staleTx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire1)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(400000))
                .montantPremierLoyer(BigDecimal.valueOf(400000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .dateCreation(LocalDateTime.now().minusHours(25))
                .dateExpiration(LocalDateTime.now().minusMinutes(30))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        MvcResult replacementRes = mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenLocataire2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")))
                .andReturn();

        TransactionEntity staleAfterCleanup = transactionRepository.findById(staleTx.getId()).orElseThrow();
        assertEquals(StatutSequestre.EXPIRE, staleAfterCleanup.getStatutSequestre());
        assertFalse(staleAfterCleanup.getDossierActif());

        JsonNode replacementJson = objectMapper.readTree(replacementRes.getResponse().getContentAsString());
        assertTrue(!staleTx.getId().toString().equals(replacementJson.get("id").asText()));
    }

    @Test
    @DisplayName("P0-3 Concurrence & Scheduler : expiration idempotente multi-instances et course paiement vs annulation")
    void testSchedulerIdempotentEtCoursesConcurrentes() throws Exception {
        // 1. Vérifier que le scheduler expire uniquement les INITIALISE échues, et ignore les non-échues et BLOQUE
        AnnonceEntity annonce2 = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Almadies P03-B")
                .description("Appartement pour test scheduler et concurrence P03-B")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(95.0)
                .nombrePieces(3)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.74)
                .longitude(-17.51)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());

        AnnonceEntity annonce3 = annonceRepository.save(AnnonceEntity.builder()
                .titre("Studio Sacré-Coeur P03-C")
                .description("Studio pour test séquestre BLOQUE préservé P03-C")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(180000))
                .superficie(45.0)
                .nombrePieces(1)
                .quartier("Sacré-Coeur")
                .ville("Dakar")
                .latitude(14.71)
                .longitude(-17.46)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.RESERVEE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());

        TransactionEntity expiredTx1 = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire1)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(400000))
                .montantPremierLoyer(BigDecimal.valueOf(400000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .dateCreation(LocalDateTime.now().minusHours(25))
                .dateExpiration(LocalDateTime.now().minusMinutes(5))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        TransactionEntity validTx2 = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce2)
                .locataire(locataire2)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(250000))
                .montantPremierLoyer(BigDecimal.valueOf(250000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .dateCreation(LocalDateTime.now())
                .dateExpiration(LocalDateTime.now().plusHours(23))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        TransactionEntity bloqueTx3 = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce3)
                .locataire(locataire1)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(180000))
                .montantPremierLoyer(BigDecimal.valueOf(180000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .referenceWave("WAVE-SANDBOX-KEEP")
                .datePaiement(LocalDateTime.now().minusHours(30))
                .dateExpiration(LocalDateTime.now().minusHours(6))
                .statutSequestre(StatutSequestre.BLOQUE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .build());

        // Simuler 2 instances parallèles exécutant le job d'expiration simultanément
        CountDownLatch readySched = new CountDownLatch(2);
        CountDownLatch startSched = new CountDownLatch(1);
        ExecutorService execSched = Executors.newFixedThreadPool(2);
        AtomicInteger totalProcessedByBoth = new AtomicInteger(0);

        Runnable schedTask = () -> {
            try {
                readySched.countDown();
                startSched.await();
                totalProcessedByBoth.addAndGet(transactionService.expirerTransactionsExpirees());
            } catch (Exception ignored) {
            }
        };
        execSched.submit(schedTask);
        execSched.submit(schedTask);
        readySched.await();
        startSched.countDown();
        execSched.shutdown();
        execSched.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, totalProcessedByBoth.get(),
                "Exactement une seule instance doit clôturer la transaction expirée en parallèle");
        assertEquals(StatutSequestre.EXPIRE, transactionRepository.findById(expiredTx1.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(validTx2.getId()).orElseThrow().getStatutSequestre());
        assertEquals(StatutSequestre.BLOQUE, transactionRepository.findById(bloqueTx3.getId()).orElseThrow().getStatutSequestre());

        // Idempotence stricte lors d'un nouvel appel du scheduler
        transactionExpirationScheduler.expirerTransactionsAbandonnees();
        assertEquals(0, transactionService.expirerTransactionsExpirees());

        // 2. Course concurrente : paiement vs annulation simultanés sur validTx2 -> exactement 1 succès (200) et 1 conflit (409)
        waveClientMock.resetCounters();
        CountDownLatch readyRace = new CountDownLatch(2);
        CountDownLatch startRace = new CountDownLatch(1);
        ExecutorService execRace = Executors.newFixedThreadPool(2);
        List<Integer> raceStatuses = Collections.synchronizedList(new ArrayList<>());

        execRace.submit(() -> {
            try {
                readyRace.countDown();
                startRace.await();
                int st = mockMvc.perform(post("/api/v1/transactions/" + validTx2.getId() + "/paiement-caution")
                                .header("Authorization", "Bearer " + tokenLocataire2))
                        .andReturn().getResponse().getStatus();
                raceStatuses.add(st);
            } catch (Exception e) {
                raceStatuses.add(500);
            }
        });

        execRace.submit(() -> {
            try {
                readyRace.countDown();
                startRace.await();
                int st = mockMvc.perform(post("/api/v1/transactions/" + validTx2.getId() + "/annuler")
                                .header("Authorization", "Bearer " + tokenLocataire2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"motif\": \"Course annulation\"}"))
                        .andReturn().getResponse().getStatus();
                raceStatuses.add(st);
            } catch (Exception e) {
                raceStatuses.add(500);
            }
        });

        readyRace.await();
        startRace.countDown();
        execRace.shutdown();
        execRace.awaitTermination(10, TimeUnit.SECONDS);

        long okCount = raceStatuses.stream().filter(s -> s == 200).count();
        long conflictCount = raceStatuses.stream().filter(s -> s == 409).count();
        assertEquals(1, okCount, "Une seule opération entre paiement et annulation doit réussir (200)");
        assertEquals(1, conflictCount, "L'opération perdante doit être rejetée proprement (409)");

        TransactionEntity finalRaceTx = transactionRepository.findById(validTx2.getId()).orElseThrow();
        if (finalRaceTx.getStatutSequestre() == StatutSequestre.BLOQUE) {
            assertEquals(1, waveClientMock.getCheckoutCallCount());
            assertNotNull(finalRaceTx.getReferenceWave());
            assertEquals(StatutPublication.RESERVEE,
                    annonceRepository.findById(annonce2.getId()).orElseThrow().getStatutPublication());
        } else {
            assertEquals(StatutSequestre.ANNULE, finalRaceTx.getStatutSequestre());
            assertFalse(finalRaceTx.getDossierActif());
            assertNull(finalRaceTx.getReferenceWave());
            assertTrue(waveClientMock.getCheckoutCallCount() <= 1);
            if (waveClientMock.getCheckoutCallCount() == 1) {
                assertTrue(
                        paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(validTx2.getId())
                                .stream()
                                .anyMatch(pw -> pw.getStatut() == StatutPaiementWave.TARDIF_A_REMBOURSER),
                        "Un appel Wave concurrent à une annulation entre Phase 1 et Phase 3 doit être marqué TARDIF_A_REMBOURSER"
                );
            }
        }
    }

    @Test
    @DisplayName("P0-3 : Course critique paiement vs expiration sur une transaction INITIALISE déjà expirée (0 appel Wave, EXPIRE garanti)")
    void testCoursePaiementVsExpirationSurTransactionDejaExpiree() throws Exception {
        TransactionEntity expiredTx = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire1)
                .proprietaire(proprietaire)
                .montantCaution(BigDecimal.valueOf(400000))
                .montantPremierLoyer(BigDecimal.valueOf(400000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dossierActif(Boolean.TRUE)
                .dateExpiration(LocalDateTime.now().minusMinutes(5))
                .build());

        waveClientMock.resetCounters();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> paymentStatuses = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> expirationResults = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                ready.countDown();
                start.await();
                int st = mockMvc.perform(post("/api/v1/transactions/" + expiredTx.getId() + "/paiement-caution")
                                .header("Authorization", "Bearer " + tokenLocataire1))
                        .andReturn().getResponse().getStatus();
                paymentStatuses.add(st);
            } catch (Exception e) {
                paymentStatuses.add(500);
            }
        });

        executor.submit(() -> {
            try {
                ready.countDown();
                start.await();
                expirationResults.add(transactionService.expirerTransactionIndividuelle(expiredTx.getId(), LocalDateTime.now()));
            } catch (Exception e) {
                expirationResults.add(false);
            }
        });

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(List.of(409), paymentStatuses,
                "Le paiement sur une transaction déjà expirée doit impérativement être rejeté en 409 Conflict");
        assertEquals(1, expirationResults.size());
        assertEquals(0, waveClientMock.getCheckoutCallCount(),
                "Aucun appel au prestataire Wave ne doit avoir lieu sur une transaction expirée");

        TransactionEntity persisted = transactionRepository.findById(expiredTx.getId()).orElseThrow();
        assertEquals(StatutSequestre.EXPIRE, persisted.getStatutSequestre());
        assertFalse(persisted.getDossierActif());
        assertNotNull(persisted.getDateCloture());
        assertEquals(StatutPublication.DISPONIBLE,
                annonceRepository.findById(annonce.getId()).orElseThrow().getStatutPublication());

        // Vérifier que locataire2 peut immédiatement réinitier sur cette annonce libérée
        mockMvc.perform(post("/api/v1/annonces/" + annonce.getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokenLocataire2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutSequestre", is("INITIALISE")));
    }
}
