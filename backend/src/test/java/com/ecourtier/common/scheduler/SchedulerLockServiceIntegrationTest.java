package com.ecourtier.common.scheduler;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.entity.NotificationSmsEntity;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.NotificationSmsRepository;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.rendezvous.service.RendezVousService;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutDocumentBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.BailPdfRecoveryScheduler;
import com.ecourtier.domain.transaction.service.TransactionExpirationScheduler;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SchedulerLockServiceIntegrationTest {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Autowired
    private SchedulerLockService schedulerLockService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RendezVousService rendezVousService;

    @Autowired
    private TransactionExpirationScheduler transactionExpirationScheduler;

    @Autowired
    private BailPdfRecoveryScheduler bailPdfRecoveryScheduler;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private RendezVousRepository rendezVousRepository;

    @Autowired
    private NotificationSmsRepository notificationSmsRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private final List<Path> generatedFilesToDelete = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanUp() {
        schedulerLockService.setClock(Clock.system(DAKAR_ZONE));
        for (Path p : generatedFilesToDelete) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
            }
        }
        generatedFilesToDelete.clear();
        testDatabaseCleaner.cleanAll();
    }

    @Test
    @DisplayName("E2 : Exclusion mutuelle concurrente inter-threads et réacquisition après libération")
    void testConcurrentMutualExclusionAndReacquireAfterRelease() throws Exception {
        int workers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch enteredLock = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger(0);
        AtomicInteger acquiredCount = new AtomicInteger(0);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            Callable<Boolean> task = () -> {
                startGate.await(5, TimeUnit.SECONDS);
                boolean acquired = schedulerLockService.tryExecuteWithLock(
                        "job-concurrent-test",
                        Duration.ofSeconds(30),
                        () -> {
                            executions.incrementAndGet();
                            enteredLock.countDown();
                            try {
                                allowRelease.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        }
                );
                if (acquired) {
                    acquiredCount.incrementAndGet();
                }
                return acquired;
            };
            futures.add(pool.submit(task));
        }

        startGate.countDown();
        assertTrue(enteredLock.await(5, TimeUnit.SECONDS), "Un worker doit entrer dans le verrou");
        // Laisser un court délai aux autres threads pour tenter l'acquisition pendant que le gagnant détient encore le bail
        Thread.sleep(150);
        assertEquals(1, executions.get(), "Un seul worker doit exécuter la tâche simultanément");
        allowRelease.countDown();

        int trueCount = 0;
        for (Future<Boolean> f : futures) {
            if (Boolean.TRUE.equals(f.get(5, TimeUnit.SECONDS))) {
                trueCount++;
            }
        }
        pool.shutdownNow();

        assertEquals(1, trueCount);
        assertEquals(1, acquiredCount.get());
        assertEquals(1, executions.get());

        // Après libération, une nouvelle exécution peut immédiatement réacquérir le verrou
        boolean reacquired = schedulerLockService.tryExecuteWithLock(
                "job-concurrent-test",
                Duration.ofSeconds(30),
                executions::incrementAndGet
        );
        assertTrue(reacquired, "Le verrou doit être réacquis après libération complète");
        assertEquals(2, executions.get());
    }

    @Test
    @DisplayName("E2 : Expiration du bail lock_until après crash simulé + protection owner contre libération tardive")
    void testLeaseExpirationRecoveryAndOwnerProtection() {
        Instant t0 = Instant.parse("2026-07-01T10:00:00Z");
        schedulerLockService.setClock(Clock.fixed(t0, DAKAR_ZONE));

        assertTrue(schedulerLockService.tryAcquire("job-crash-test", Duration.ofSeconds(60), "instance-crashed"));

        // T0 + 20s : toujours verrouillé par instance-crashed
        schedulerLockService.setClock(Clock.fixed(t0.plusSeconds(20), DAKAR_ZONE));
        assertFalse(schedulerLockService.tryAcquire("job-crash-test", Duration.ofSeconds(60), "instance-healthy"));

        // T0 + 61s : bail expiré -> instance-healthy reprend le verrou
        schedulerLockService.setClock(Clock.fixed(t0.plusSeconds(61), DAKAR_ZONE));
        assertTrue(schedulerLockService.tryAcquire("job-crash-test", Duration.ofSeconds(60), "instance-healthy"));

        String currentOwner = jdbcTemplate.queryForObject(
                "SELECT locked_by FROM scheduler_locks WHERE name = ?",
                String.class,
                "job-crash-test"
        );
        assertEquals("instance-healthy", currentOwner);

        // Si l'ancienne instance se réveille tardivement et appelle release(), elle ne doit pas casser le verrou d'instance-healthy
        schedulerLockService.release("job-crash-test", Duration.ZERO, "instance-crashed");
        assertFalse(schedulerLockService.tryAcquire("job-crash-test", Duration.ofSeconds(60), "instance-third"),
                "Une libération d'un ancien propriétaire ne doit pas supprimer le verrou d'instance-healthy");
    }

    @Test
    @DisplayName("E2 : Les 3 jobs @Scheduled métier respectent leurs verrous SQL et reprennent après libération")
    void testAllThreeBusinessSchedulersRespectSchedulerLocks() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String phoneSeed = String.valueOf(Math.abs(suffix.hashCode() % 10000000));

        UtilisateurEntity proprio = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diop").prenom("Ibrahima").email("prop.lock." + suffix + "@ecourtier.sn")
                .motDePasse("Pass1234!").telephone("+221774" + phoneSeed)
                .role(Role.PROPRIETAIRE).actif(true).build());
        UtilisateurEntity locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall").prenom("Mariama").email("loc.lock." + suffix + "@ecourtier.sn")
                .motDePasse("Pass1234!").telephone("+221775" + phoneSeed)
                .role(Role.LOCATAIRE).actif(true).build());

        AnnonceEntity annonceRdv = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Lock RDV")
                .description("Test verrou RDV")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(90.0)
                .nombrePieces(3)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.7167)
                .longitude(-17.4677)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprio)
                .build());

        AnnonceEntity annonceExp = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Lock Exp")
                .description("Test verrou expiration")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(150000))
                .superficie(40.0)
                .nombrePieces(1)
                .quartier("Sacré-Cœur")
                .ville("Dakar")
                .latitude(14.7222)
                .longitude(-17.4667)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.RESERVEE)
                .supprime(false)
                .utilisateur(proprio)
                .build());

        AnnonceEntity annoncePdf = annonceRepository.save(AnnonceEntity.builder()
                .titre("Villa Lock PDF")
                .description("Test verrou PDF")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(450000))
                .superficie(200.0)
                .nombrePieces(5)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.74)
                .longitude(-17.51)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.LOUEE)
                .supprime(false)
                .utilisateur(proprio)
                .build());

        // 1. RDV rappel demain
        LocalDate demainDakar = LocalDate.now(DAKAR_ZONE).plusDays(1);
        RendezVousEntity rdv = rendezVousRepository.save(RendezVousEntity.builder()
                .annonce(annonceRdv)
                .demandeur(locataire)
                .dateHeure(demainDakar.atTime(10, 0))
                .statut(StatutRendezVous.CONFIRME)
                .rappelEnvoye(false)
                .build());

        assertTrue(schedulerLockService.tryAcquire(RendezVousService.REMINDER_LOCK_NAME, Duration.ofMinutes(2), "remote-pod"));
        assertEquals(0, rendezVousService.simulerRappelsDemain());
        assertFalse(Boolean.TRUE.equals(rendezVousRepository.findById(rdv.getId()).orElseThrow().getRappelEnvoye()));
        assertEquals(0, notificationSmsRepository.findByRendezVousIdOrderByDateCreationAsc(rdv.getId()).size());

        schedulerLockService.release(RendezVousService.REMINDER_LOCK_NAME, Duration.ZERO, "remote-pod");
        assertEquals(1, rendezVousService.simulerRappelsDemain());
        assertTrue(Boolean.TRUE.equals(rendezVousRepository.findById(rdv.getId()).orElseThrow().getRappelEnvoye()));
        List<NotificationSmsEntity> smsList = notificationSmsRepository.findByRendezVousIdOrderByDateCreationAsc(rdv.getId());
        assertEquals(1, smsList.size());

        // 2. Expiration transactions abandonnées
        TransactionEntity txExp = transactionRepository.save(TransactionEntity.builder()
                .annonce(annonceExp)
                .locataire(locataire)
                .proprietaire(proprio)
                .montantCaution(BigDecimal.valueOf(150000))
                .montantPremierLoyer(BigDecimal.valueOf(150000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .statutDocumentBail(StatutDocumentBail.NON_GENERE)
                .dateExpiration(LocalDateTime.now(DAKAR_ZONE).minusMinutes(10))
                .dossierActif(Boolean.TRUE)
                .build());

        assertTrue(schedulerLockService.tryAcquire(TransactionExpirationScheduler.LOCK_NAME, Duration.ofMinutes(2), "remote-pod"));
        transactionExpirationScheduler.expirerTransactionsAbandonnees();
        assertEquals(StatutSequestre.INITIALISE, transactionRepository.findById(txExp.getId()).orElseThrow().getStatutSequestre());

        schedulerLockService.release(TransactionExpirationScheduler.LOCK_NAME, Duration.ZERO, "remote-pod");
        transactionExpirationScheduler.expirerTransactionsAbandonnees();
        assertEquals(StatutSequestre.EXPIRE, transactionRepository.findById(txExp.getId()).orElseThrow().getStatutSequestre());

        // 3. Reprise PDF de bail en échec
        TransactionEntity txPdf = transactionRepository.save(TransactionEntity.builder()
                .annonce(annoncePdf)
                .locataire(locataire)
                .proprietaire(proprio)
                .montantCaution(BigDecimal.valueOf(450000))
                .montantPremierLoyer(BigDecimal.valueOf(450000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .statutDocumentBail(StatutDocumentBail.ECHEC)
                .pdfBailTentatives(1)
                .pdfBailErreur("IO passagère")
                .dateSignatureLocataire(LocalDateTime.now(DAKAR_ZONE).minusMinutes(5))
                .dateSignatureProprietaire(LocalDateTime.now(DAKAR_ZONE).minusMinutes(4))
                .dateLiberation(LocalDateTime.now(DAKAR_ZONE).minusMinutes(4))
                .dossierActif(Boolean.TRUE)
                .build());

        assertTrue(schedulerLockService.tryAcquire(BailPdfRecoveryScheduler.LOCK_NAME, Duration.ofMinutes(2), "remote-pod"));
        bailPdfRecoveryScheduler.reprendrePdfEnEchec();
        TransactionEntity stillFailed = transactionRepository.findById(txPdf.getId()).orElseThrow();
        assertEquals(StatutDocumentBail.ECHEC, stillFailed.getStatutDocumentBail());
        assertEquals(1, stillFailed.getPdfBailTentatives());

        schedulerLockService.release(BailPdfRecoveryScheduler.LOCK_NAME, Duration.ZERO, "remote-pod");
        bailPdfRecoveryScheduler.reprendrePdfEnEchec();
        TransactionEntity recovered = transactionRepository.findById(txPdf.getId()).orElseThrow();
        assertEquals(StatutDocumentBail.GENERE, recovered.getStatutDocumentBail());
        assertEquals(2, recovered.getPdfBailTentatives());
        assertNotNull(recovered.getPdfBailPath());
        generatedFilesToDelete.add(Path.of(recovered.getPdfBailPath()));
        assertTrue(Files.isRegularFile(Path.of(recovered.getPdfBailPath())));
    }
}
