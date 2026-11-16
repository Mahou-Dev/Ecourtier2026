package com.ecourtier.domain.transaction.service;

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
import com.ecourtier.domain.transaction.dto.RemboursementRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionRequestDTO;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.PaiementWaveEntity;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutPaiementWave;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.entity.TypeOperationWave;
import com.ecourtier.domain.transaction.repository.PaiementWaveRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TransactionClockAndRefundDossierIntegrationTest {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WavePaymentReconciliationService reconciliationService;

    @Autowired
    private WaveClientMock waveClientMock;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;
    private UtilisateurEntity admin;
    private TimeZone previousTimeZone;

    @BeforeEach
    void setUp() {
        previousTimeZone = TimeZone.getDefault();
        waveClientMock.resetState();
        cleanup();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ba")
                .prenom("Moussa")
                .email("moussa.res07@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770007701")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Awa")
                .email("awa.res07@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770007702")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        admin = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Admin")
                .prenom("Dakar")
                .email("admin.res07@ecourtier.sn")
                .motDePasse(passwordEncoder.encode("Pass123!"))
                .telephone("+221770007700")
                .role(Role.ADMIN)
                .actif(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        transactionService.setClock(Clock.system(DAKAR_ZONE));
        TimeZone.setDefault(previousTimeZone);
        waveClientMock.resetState();
        cleanup();
    }

    private void cleanup() {
        testDatabaseCleaner.cleanAll();
    }

    private AnnonceEntity createVerifiedAnnonce(String titre) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre(titre)
                .description("Annonce de test RES-07")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("250000"))
                .superficie(90.0)
                .nombrePieces(3)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.6928)
                .longitude(-17.4467)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(proprietaire)
                .build());
    }

    @Test
    @DisplayName("RES-07 : Horloge fixe & Africa/Dakar respectés sans écrasement Hibernate @CreationTimestamp sous TZ Europe/Paris")
    void testClockFixeEtAfriqueDakarSousTimezoneParis() {
        AnnonceEntity annonce = createVerifiedAnnonce("Appartement Plateau RES-07");
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));

        // 1. Vérifier que le défaut sans override utilise Africa/Dakar (+00:00) et non Europe/Paris (+02:00 en été / +01:00 en hiver)
        transactionService.setClock(Clock.system(DAKAR_ZONE));
        RendezVousEntity rdv = rendezVousRepository.saveAndFlush(RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.of(2026, 12, 20, 10, 0))
                .statut(StatutRendezVous.EFFECTUE)
                .build());

        LocalDateTime dakarNow = LocalDateTime.now(DAKAR_ZONE);
        long driftSeconds = Math.abs(Duration.between(rdv.getDateCreation(), dakarNow).toSeconds());
        assertTrue(driftSeconds < 30, "dateCreation du RDV doit être en heure Africa/Dakar même sous TZ Europe/Paris (drift=" + driftSeconds + "s)");

        // 2. Injecter une horloge fixe à 2026-06-15T23:30:00Z (en juin: Dakar=23:30 le 15, Paris=01:30 le 16)
        Instant instantFixe = Instant.parse("2026-06-15T23:30:00Z");
        transactionService.setClock(Clock.fixed(instantFixe, DAKAR_ZONE));

        TransactionResponseDTO created = transactionService.initierTransaction(
                annonce.getId(),
                TransactionRequestDTO.builder().build(),
                locataire.getEmail()
        );

        TransactionEntity reloaded = transactionRepository.findById(created.getId()).orElseThrow();
        LocalDateTime expectedCreation = LocalDateTime.of(2026, 6, 15, 23, 30, 0);
        LocalDateTime expectedExpiration = expectedCreation.plusHours(24);

        assertEquals(expectedCreation, reloaded.getDateCreation(),
                "Hibernate ne doit pas écraser dateCreation injectée par Clock");
        assertEquals(expectedExpiration, reloaded.getDateExpiration(),
                "dateExpiration doit être exactement dateCreation + 24h en heure Africa/Dakar");
    }

    @Test
    @DisplayName("RES-07 : Remboursement (direct et réconcilié) bascule dossierActif=false alors que LIBERE garde dossierActif=true")
    void testDossierActifFalseSurRemboursementEtTrueSurLibere() {
        // Cas A : remboursement direct -> BLOQUE -> REMBOURSE et dossierActif = false
        AnnonceEntity annonceA = createVerifiedAnnonce("Annonce Remboursement Direct");
        rendezVousRepository.saveAndFlush(RendezVousEntity.builder()
                .annonce(annonceA)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.of(2026, 12, 20, 10, 0))
                .statut(StatutRendezVous.EFFECTUE)
                .build());
        TransactionResponseDTO initiatedA = transactionService.initierTransaction(
                annonceA.getId(), TransactionRequestDTO.builder().build(), locataire.getEmail());
        TransactionResponseDTO paidA = transactionService.effectuerPaiementCaution(initiatedA.getId(), locataire.getEmail());
        assertEquals(StatutSequestre.BLOQUE, paidA.getStatutSequestre());
        assertTrue(Boolean.TRUE.equals(paidA.getDossierActif()));

        TransactionResponseDTO refundedA = transactionService.rembourserTransaction(
                paidA.getId(),
                RemboursementRequestDTO.builder().motif("Annulation justifiée avant signatures").build(),
                admin.getEmail()
        );
        assertEquals(StatutSequestre.REMBOURSE, refundedA.getStatutSequestre());
        assertFalse(Boolean.TRUE.equals(refundedA.getDossierActif()));

        TransactionEntity entityA = transactionRepository.findById(paidA.getId()).orElseThrow();
        assertEquals(StatutSequestre.REMBOURSE, entityA.getStatutSequestre());
        assertFalse(Boolean.TRUE.equals(entityA.getDossierActif()), "Un dossier REMBOURSE ne doit plus avoir dossierActif=true");

        // Cas B : remboursement réconcilié (finalizeReconciledRefund) -> BLOQUE -> REMBOURSE et dossierActif = false
        AnnonceEntity annonceB = createVerifiedAnnonce("Annonce Remboursement Reconcilie");
        rendezVousRepository.saveAndFlush(RendezVousEntity.builder()
                .annonce(annonceB)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.of(2026, 12, 21, 10, 0))
                .statut(StatutRendezVous.EFFECTUE)
                .build());
        TransactionResponseDTO initiatedB = transactionService.initierTransaction(
                annonceB.getId(), TransactionRequestDTO.builder().build(), locataire.getEmail());
        TransactionResponseDTO paidB = transactionService.effectuerPaiementCaution(initiatedB.getId(), locataire.getEmail());
        TransactionEntity entityB = transactionRepository.findById(paidB.getId()).orElseThrow();

        var waveRefund = waveClientMock.initiateEscrowRefund(
                entityB.getId(),
                entityB.getReferenceWave(),
                entityB.getMontantTotalSequestre()
        );
        paiementWaveRepository.saveAndFlush(PaiementWaveEntity.builder()
                .transaction(entityB)
                .typeOperation(TypeOperationWave.REMBOURSEMENT)
                .statut(StatutPaiementWave.A_RECONCILIER)
                .cleIdempotence("reconcile-refund-" + UUID.randomUUID())
                .referenceExterne(waveRefund.getRefundReference())
                .montant(entityB.getMontantTotalSequestre())
                .build());

        int reconciled = reconciliationService.reconcilierPaiementsEnAttente();
        assertEquals(1, reconciled);

        TransactionEntity entityBReconciled = transactionRepository.findById(paidB.getId()).orElseThrow();
        assertEquals(StatutSequestre.REMBOURSE, entityBReconciled.getStatutSequestre());
        assertFalse(Boolean.TRUE.equals(entityBReconciled.getDossierActif()), "finalizeReconciledRefund doit aussi passer dossierActif à false");
        assertEquals(StatutPublication.DISPONIBLE, annonceRepository.findById(annonceB.getId()).orElseThrow().getStatutPublication());

        // Cas C : double signature complète -> BLOQUE -> LIBERE garde dossierActif = true
        AnnonceEntity annonceC = createVerifiedAnnonce("Annonce Liberee");
        rendezVousRepository.saveAndFlush(RendezVousEntity.builder()
                .annonce(annonceC)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.of(2026, 12, 22, 10, 0))
                .statut(StatutRendezVous.EFFECTUE)
                .build());
        TransactionResponseDTO initiatedC = transactionService.initierTransaction(
                annonceC.getId(), TransactionRequestDTO.builder().build(), locataire.getEmail());
        transactionService.effectuerPaiementCaution(initiatedC.getId(), locataire.getEmail());
        transactionService.signerBail(initiatedC.getId(), locataire.getEmail());
        TransactionResponseDTO releasedC = transactionService.signerBail(initiatedC.getId(), proprietaire.getEmail());

        assertEquals(StatutSequestre.LIBERE, releasedC.getStatutSequestre());
        assertEquals(StatutBail.SIGNE_COMPLET, releasedC.getStatutBail());
        assertTrue(Boolean.TRUE.equals(releasedC.getDossierActif()), "Un dossier LIBERE doit conserver dossierActif=true");
    }
}
