package com.ecourtier.common.db;

import com.ecourtier.common.scheduler.SchedulerLockService;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutDocumentBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.transaction.service.BailPdfRecoveryScheduler;
import com.ecourtier.domain.transaction.service.TransactionService;
import org.springframework.web.server.ResponseStatusException;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@EnabledIfSystemProperty(named = "ecourtier.test.pg16", matches = "true")
@DisplayName("Phase 3 — Validation d'intégration sur PostgreSQL 16 réel (Testcontainers)")
class Postgres16Phase3IntegrationTest {

    static {
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.44");
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ecourtier_pg16_test")
            .withUsername("ecourtier_test")
            .withPassword("ecourtier_test_pwd");

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SchedulerLockService schedulerLockService;

    @Autowired
    private BailPdfRecoveryScheduler bailPdfRecoveryScheduler;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private PaiementWaveRepository paiementWaveRepository;

    @Test
    @DisplayName("1. Flyway V1 à V21 appliquées sans erreur sur PostgreSQL 16 + validation Hibernate + index partiels")
    void testFlywayV1ToV20AndSchemaIntegrityOnPostgres16() {
        String pgVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        assertNotNull(pgVersion);
        assertTrue(pgVersion.startsWith("16."), "Attendu PostgreSQL 16.x, obtenu : " + pgVersion);

        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertNotNull(appliedMigrations);
        assertTrue(appliedMigrations >= 21, "Toutes les migrations V1..V21 doivent être appliquées avec succès");

        String v20Script = jdbcTemplate.queryForObject(
                "SELECT script FROM flyway_schema_history WHERE version = '20' AND success = true",
                String.class);
        assertEquals("V20__phase3_security_pdf_and_scheduler_locks.sql", v20Script);

        String v21Script = jdbcTemplate.queryForObject(
                "SELECT script FROM flyway_schema_history WHERE version = '21' AND success = true",
                String.class);
        assertEquals("V21__add_message_sequence_order_to_messages_ia.sql", v21Script);

        // Vérifier la présence des index partiels PostgreSQL (V14/V19) et de la contrainte CHECK V20
        List<String> indexNames = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
        assertTrue(indexNames.contains("uq_transactions_annonce_active"),
                "L'index partiel uq_transactions_annonce_active doit exister sur PostgreSQL 16");
        assertTrue(indexNames.contains("idx_refresh_tokens_utilisateur_actif"),
                "L'index partiel idx_refresh_tokens_utilisateur_actif doit exister sur PostgreSQL 16");
        assertTrue(indexNames.contains("refresh_tokens_token_hash_key"),
                "L'index unique refresh_tokens_token_hash_key doit exister sur PostgreSQL 16");

        List<String> constraintNames = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conname = 'chk_transactions_statut_document_bail'",
                String.class);
        assertFalse(constraintNames.isEmpty(),
                "La contrainte CHECK chk_transactions_statut_document_bail (V20) doit exister sur PostgreSQL 16");
    }

    @Test
    @DisplayName("2. Phase A1/B1 sur PostgreSQL 16 : token_version par défaut à 0 et verrouillage optimiste @Version sur AnnonceEntity")
    void testTokenVersionAndOptimisticLockingOnPostgres16() {
        UtilisateurEntity owner = UtilisateurEntity.builder()
                .email("pg16.owner." + UUID.randomUUID() + "@test.sn")
                .motDePasse("HashTest123!")
                .prenom("Mamadou")
                .nom("Ndour")
                .telephone("+22177" + String.valueOf(Math.abs(UUID.randomUUID().hashCode())).substring(0, 7))
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build();
        owner = utilisateurRepository.saveAndFlush(owner);
        assertEquals(0L, owner.getTokenVersion());

        AnnonceEntity annonce = AnnonceEntity.builder()
                .titre("Villa Almadies PG16 Test")
                .description("Villa moderne testée sur PostgreSQL 16 réel avec verrouillage optimiste.")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("850000"))
                .superficie(240.0)
                .nombrePieces(6)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.745)
                .longitude(-17.515)
                .meuble(true)
                .animauxAcceptes(true)
                .confirmationAutomatique(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(owner)
                .build();
        annonce = annonceRepository.saveAndFlush(annonce);
        assertEquals(0L, annonce.getVersion());

        // Simuler une modification concurrente en base (version passe de 0 à 1)
        jdbcTemplate.update("UPDATE annonces SET version = version + 1, titre = 'Modifiée Concurrente' WHERE id = ?",
                annonce.getId());

        // Tenter de sauvegarder l'entité détentrice de l'ancienne version 0 -> ObjectOptimisticLockingFailureException
        AnnonceEntity staleCopy = annonce;
        staleCopy.setTitre("Écrasement Perdu");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            annonceRepository.saveAndFlush(staleCopy);
        });
    }

    @Test
    @DisplayName("3. Phase C sur PostgreSQL 16 : contrainte CHECK statut_document_bail, index partiel uq_transactions_annonce_active et reprise BailPdfRecoveryScheduler")
    void testTransactionPartialIndexAndPdfRecoveryOnPostgres16() {
        UtilisateurEntity owner = utilisateurRepository.saveAndFlush(UtilisateurEntity.builder()
                .email("pg16.bailleur." + UUID.randomUUID() + "@test.sn")
                .motDePasse("HashTest123!")
                .prenom("Ousmane")
                .nom("Sow")
                .telephone("+22176" + String.valueOf(Math.abs(UUID.randomUUID().hashCode())).substring(0, 7))
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        UtilisateurEntity locataire = utilisateurRepository.saveAndFlush(UtilisateurEntity.builder()
                .email("pg16.locataire." + UUID.randomUUID() + "@test.sn")
                .motDePasse("HashTest123!")
                .prenom("Aissatou")
                .nom("Ba")
                .telephone("+22178" + String.valueOf(Math.abs(UUID.randomUUID().hashCode())).substring(0, 7))
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        AnnonceEntity annonce = annonceRepository.saveAndFlush(AnnonceEntity.builder()
                .titre("Appartement Mermoz PG16")
                .description("Appartement testant l'index unique partiel et la reprise PDF sur PG16.")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("400000"))
                .superficie(110.0)
                .nombrePieces(4)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.708)
                .longitude(-17.475)
                .meuble(false)
                .animauxAcceptes(false)
                .confirmationAutomatique(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(owner)
                .build());

        // 1. Tester l'index partiel uq_transactions_annonce_active (WHERE statut_sequestre IN ('INITIALISE', 'BLOQUE') AND dossier_actif = true)
        TransactionEntity activeInitialiseTx = transactionRepository.saveAndFlush(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(owner)
                .montantCaution(new BigDecimal("400000"))
                .montantPremierLoyer(new BigDecimal("400000"))
                .fraisDossier(new BigDecimal("5000"))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .statutDocumentBail(StatutDocumentBail.NON_GENERE)
                .dossierActif(true)
                .build());

        TransactionEntity duplicateActiveTx = TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(owner)
                .montantCaution(new BigDecimal("400000"))
                .montantPremierLoyer(new BigDecimal("400000"))
                .fraisDossier(new BigDecimal("5000"))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .statutDocumentBail(StatutDocumentBail.NON_GENERE)
                .dossierActif(true)
                .build();
        assertThrows(DataIntegrityViolationException.class, () -> {
            transactionRepository.saveAndFlush(duplicateActiveTx);
        });

        // Faire évoluer la transaction vers LIBERE + SIGNE_COMPLET + ECHEC pour tester la reprise PDF
        LocalDateTime nowDakar = LocalDateTime.now(ZoneId.of("Africa/Dakar"));
        activeInitialiseTx.setStatutSequestre(StatutSequestre.LIBERE);
        activeInitialiseTx.setStatutBail(StatutBail.SIGNE_COMPLET);
        activeInitialiseTx.setStatutDocumentBail(StatutDocumentBail.ECHEC);
        activeInitialiseTx.setPdfBailErreur("Erreur simulée initiale");
        activeInitialiseTx.setPdfBailTentatives(1);
        activeInitialiseTx.setDateSignatureLocataire(nowDakar.minusMinutes(10));
        activeInitialiseTx.setDateSignatureProprietaire(nowDakar.minusMinutes(5));
        activeInitialiseTx.setDateLiberation(nowDakar.minusMinutes(5));
        TransactionEntity tx1 = transactionRepository.saveAndFlush(activeInitialiseTx);

        // Vérifier que la contrainte CHECK chk_transactions_statut_document_bail rejette une valeur invalide en SQL
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("UPDATE transactions SET statut_document_bail = 'STATUT_INVALIDE' WHERE id = ?",
                    tx1.getId());
        });

        // Exécuter le scheduler de reprise PDF sur PostgreSQL 16 et vérifier le passage à GENERE
        bailPdfRecoveryScheduler.reprendrePdfEnEchec();

        TransactionEntity recovered = transactionRepository.findById(tx1.getId()).orElseThrow();
        assertEquals(StatutDocumentBail.GENERE, recovered.getStatutDocumentBail());
        assertNull(recovered.getPdfBailErreur());
        assertNotNull(recovered.getPdfBailPath());
        File pdfFile = new File(recovered.getPdfBailPath());
        try {
            assertTrue(pdfFile.exists() && pdfFile.length() > 0, "Le fichier PDF régénéré doit exister sur disque");
        } finally {
            if (pdfFile.exists()) {
                pdfFile.delete();
            }
        }
    }

    @Test
    @DisplayName("4. Phase E2 sur PostgreSQL 16 : exclusion mutuelle et reprise de bail expiré dans scheduler_locks")
    void testSchedulerLocksMutualExclusionAndExpiredLeaseTakeoverOnPostgres16() {
        String lockName = "pg16-test-lock-" + UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger(0);

        boolean firstRun = schedulerLockService.tryExecuteWithLock(lockName, Duration.ofMinutes(5), executions::incrementAndGet);
        assertTrue(firstRun);
        assertEquals(1, executions.get());

        // Après release(), la ligne existe dans scheduler_locks avec lock_until <= now.
        // Simuler une autre instance qui détient activement le verrou dans le futur (lock_until = now + 5 min)
        LocalDateTime nowDakar = LocalDateTime.now(ZoneId.of("Africa/Dakar"));
        jdbcTemplate.update(
                "UPDATE scheduler_locks SET locked_by = ?, locked_at = ?, lock_until = ? WHERE name = ?",
                "other-instance-active",
                Timestamp.valueOf(nowDakar.minusSeconds(10)),
                Timestamp.valueOf(nowDakar.plusMinutes(5)),
                lockName
        );

        boolean contendedRun = schedulerLockService.tryExecuteWithLock(lockName, Duration.ofMinutes(5), executions::incrementAndGet);
        assertFalse(contendedRun, "Le verrou détenu par une autre instance active ne doit pas être acquis");
        assertEquals(1, executions.get());

        // Simuler l'expiration du bail (lock_until dans le passé) -> reprise atomique autorisée
        jdbcTemplate.update(
                "UPDATE scheduler_locks SET lock_until = ? WHERE name = ?",
                Timestamp.valueOf(nowDakar.minusSeconds(5)),
                lockName
        );

        boolean takeoverRun = schedulerLockService.tryExecuteWithLock(lockName, Duration.ofMinutes(5), executions::incrementAndGet);
        assertTrue(takeoverRun, "Un verrou expiré doit être repris atomiquement sur PostgreSQL 16");
        assertEquals(2, executions.get());
    }

    @Test
    @DisplayName("5. V23 sur PostgreSQL 16 : sale_complete puis refund_complete avec même ref_command/token sans collision d'index unique")
    void testPayTechSaleThenRefundSameRefCommandAndTokenOnPostgres16() {
        UtilisateurEntity owner = utilisateurRepository.saveAndFlush(UtilisateurEntity.builder()
                .email("pg16.pt.owner." + UUID.randomUUID() + "@test.sn")
                .motDePasse("HashTest123!")
                .prenom("Modou")
                .nom("Diop")
                .telephone("+22177" + String.valueOf(Math.abs(UUID.randomUUID().hashCode()) + 1000000).substring(0, 7))
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        UtilisateurEntity locataire = utilisateurRepository.saveAndFlush(UtilisateurEntity.builder()
                .email("pg16.pt.loc." + UUID.randomUUID() + "@test.sn")
                .motDePasse("HashTest123!")
                .prenom("Fatou")
                .nom("Ndiaye")
                .telephone("+22178" + String.valueOf(Math.abs(UUID.randomUUID().hashCode()) + 1000000).substring(0, 7))
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        AnnonceEntity annonce = annonceRepository.saveAndFlush(AnnonceEntity.builder()
                .titre("Studio Point E PG16 PayTech")
                .description("Test PostgreSQL 16 index uniques V19/V23 sur sale_complete puis refund_complete.")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("200000"))
                .superficie(45.0)
                .nombrePieces(2)
                .quartier("Point E")
                .ville("Dakar")
                .latitude(14.695)
                .longitude(-17.460)
                .meuble(false)
                .animauxAcceptes(false)
                .confirmationAutomatique(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(owner)
                .build());

        String refCommand = "ECOURTIER-PG16-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String token = "tok_pg16_" + UUID.randomUUID().toString().substring(0, 8);
        TransactionEntity tx = transactionRepository.saveAndFlush(TransactionEntity.builder()
                .annonce(annonce)
                .locataire(locataire)
                .proprietaire(owner)
                .montantCaution(new BigDecimal("200000"))
                .montantPremierLoyer(new BigDecimal("200000"))
                .fraisDossier(new BigDecimal("5000"))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .statutDocumentBail(StatutDocumentBail.NON_GENERE)
                .dossierActif(true)
                .refCommandPaytech(refCommand)
                .tokenPaiementExterne(token)
                .statutPaiementExterne("EN_ATTENTE_CONFIRMATION")
                .dateExpiration(LocalDateTime.now(ZoneId.of("Africa/Dakar")).plusHours(24))
                .build());

        // 1. Confirmation paiement (crée ligne PAIEMENT_SEQUESTRE avec ref_command et reference_externe=token)
        transactionService.confirmExternalPayment(
                tx.getId(), null, token, refCommand, "PAYTECH", false, new BigDecimal("405000"), "{\"type_event\":\"sale_complete\"}");

        // 2. Notification transfert pointant sur un token PAIEMENT_SEQUESTRE -> doit être rejetée 404
        ResponseStatusException transferEx = assertThrows(ResponseStatusException.class, () ->
                transactionService.handleExternalTransferNotification(token, null, false, "failed", "{}"));
        assertEquals(404, transferEx.getStatusCode().value());

        // 3. Remboursement IPN avec MÊME ref_command et MÊME token (et rejeu idempotent) -> aucune violation d'index PostgreSQL !
        var refunded = transactionService.handleExternalRefundNotification(
                tx.getId(), refCommand, token, new BigDecimal("405000"), "{\"type_event\":\"refund_complete\"}");
        assertEquals(StatutSequestre.REMBOURSE, refunded.getStatutSequestre());

        var replayed = transactionService.handleExternalRefundNotification(
                tx.getId(), refCommand, token, new BigDecimal("405000"), "{\"type_event\":\"refund_complete\"}");
        assertEquals(StatutSequestre.REMBOURSE, replayed.getStatutSequestre());

        List<PaiementWaveEntity> rows = paiementWaveRepository.findByTransactionIdOrderByDateCreationDesc(tx.getId());
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.getTypeOperation() == TypeOperationWave.PAIEMENT_SEQUESTRE && refCommand.equals(r.getRefCommand())));
        assertTrue(rows.stream().anyMatch(r -> r.getTypeOperation() == TypeOperationWave.REMBOURSEMENT && ("REFUND-" + token).equals(r.getReferenceExterne())));
    }
}
