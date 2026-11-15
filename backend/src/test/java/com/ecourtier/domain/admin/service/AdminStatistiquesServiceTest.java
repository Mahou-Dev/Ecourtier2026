package com.ecourtier.domain.admin.service;

import com.ecourtier.domain.admin.dto.AdminStatistiquesDTO;
import com.ecourtier.domain.admin.repository.AdminStatistiquesRepository;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminStatistiquesServiceTest {

    @Autowired
    private AdminStatistiquesService adminStatistiquesService;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AdminStatistiquesRepository adminStatistiquesRepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.ConversationIARepository conversationIARepository;

    @Autowired
    private com.ecourtier.domain.ia.repository.MessageIARepository messageIARepository;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private UtilisateurEntity testUser;

    @BeforeEach
    void setUp() {
        if (cacheManager.getCache("adminStats") != null) {
            cacheManager.getCache("adminStats").clear();
        }
        transactionRepository.deleteAll();
        messageIARepository.deleteAll();
        conversationIARepository.deleteAll();
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        testUser = UtilisateurEntity.builder()
                .nom("Diallo")
                .prenom("Mamadou")
                .email("mamadou.diallo@ecourtier.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 000 00 00")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build();
        utilisateurRepository.save(testUser);
    }

    @Test
    @DisplayName("Vérification des comptages d'annonces par statutPublication (ex: 3 DISPONIBLE, 2 LOUEE)")
    void testAgregationsStatutPublicationExactes() {
        // Créer 3 annonces DISPONIBLE
        for (int i = 0; i < 3; i++) {
            AnnonceEntity a = AnnonceEntity.builder()
                    .titre("Studio Plateau " + i)
                    .description("Charmant studio aux Almadies")
                    .type(TypeBien.STUDIO)
                    .modeTransaction(ModeTransaction.LOCATION)
                    .prix(BigDecimal.valueOf(150000))
                    .superficie(35.0)
                    .nombrePieces(1)
                    .quartier("Almadies")
                    .ville("Dakar")
                    .latitude(14.74)
                    .longitude(-17.51)
                    .statutPublication(StatutPublication.DISPONIBLE)
                    .statutVerification(StatutVerification.VERIFIEE)
                    .utilisateur(testUser)
                    .dateCreation(LocalDateTime.now())
                    .build();
            annonceRepository.save(a);
        }

        // Créer 2 annonces LOUEE
        for (int i = 0; i < 2; i++) {
            AnnonceEntity a = AnnonceEntity.builder()
                    .titre("Appartement Fann " + i)
                    .description("Grand appartement 3 pièces")
                    .type(TypeBien.APPARTEMENT)
                    .modeTransaction(ModeTransaction.LOCATION)
                    .prix(BigDecimal.valueOf(350000))
                    .superficie(80.0)
                    .nombrePieces(3)
                    .quartier("Fann")
                    .ville("Dakar")
                    .latitude(14.69)
                    .longitude(-17.46)
                    .statutPublication(StatutPublication.LOUEE)
                    .statutVerification(StatutVerification.VERIFIEE)
                    .utilisateur(testUser)
                    .dateCreation(LocalDateTime.now())
                    .build();
            annonceRepository.save(a);
        }

        AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques("30j");

        assertNotNull(stats);
        assertNotNull(stats.getAnnonces());
        assertEquals(3L, stats.getAnnonces().getTotalParStatutPublication().get("DISPONIBLE"));
        assertEquals(2L, stats.getAnnonces().getTotalParStatutPublication().get("LOUEE"));
        assertEquals(0L, stats.getAnnonces().getTotalParStatutPublication().get("VENDUE"));
    }

    @Test
    @DisplayName("Vérification que les taux de conversion ne provoquent pas de division par zéro quand dénominateur = 0")
    void testProtectionDivisionParZero() {
        AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques("30j");

        assertNotNull(stats);
        assertNotNull(stats.getActiviteCommerciale());
        assertNotNull(stats.getAssistantIA());

        // Aucun RDV -> taux conversion RDV -> Transaction doit être 0.0 (pas NaN ni exception)
        assertEquals(0.0, stats.getActiviteCommerciale().getTauxConversionRdvToTransaction());

        // Aucune transaction -> taux conversion Transaction -> Bail doit être 0.0
        assertEquals(0.0, stats.getActiviteCommerciale().getTauxConversionTransactionToBail());

        // Aucune conversation IA -> taux shortlist doit être 0.0
        assertEquals(0.0, stats.getAssistantIA().getTauxConversationsShortlistNonVide());
    }

    @Test
    @DisplayName("Vérification du tauxConversationsShortlistNonVide avec 2 conversations (Conversation A avec nombreAnnoncesSuggerees=2, Conversation B avec 0) => 50.0%")
    void testCalculTauxShortlistDeuxConversations() {
        // Conversation A : 1 message ASSISTANT avec nombreAnnoncesSuggerees = 2
        com.ecourtier.domain.ia.entity.ConversationIAEntity convA = com.ecourtier.domain.ia.entity.ConversationIAEntity.builder()
                .utilisateur(testUser)
                .dateCreation(LocalDateTime.now())
                .build();
        conversationIARepository.saveAndFlush(convA);

        com.ecourtier.domain.ia.entity.MessageIAEntity msgA = com.ecourtier.domain.ia.entity.MessageIAEntity.builder()
                .conversation(convA)
                .role(com.ecourtier.domain.ia.entity.RoleMessageIA.ASSISTANT)
                .contenu("J'ai trouvé 2 annonce(s) correspondant à votre recherche")
                .nombreAnnoncesSuggerees(2)
                .dateEnvoi(LocalDateTime.now())
                .build();
        messageIARepository.saveAndFlush(msgA);

        // Conversation B : 1 message ASSISTANT avec nombreAnnoncesSuggerees = 0
        com.ecourtier.domain.ia.entity.ConversationIAEntity convB = com.ecourtier.domain.ia.entity.ConversationIAEntity.builder()
                .utilisateur(testUser)
                .dateCreation(LocalDateTime.now())
                .build();
        conversationIARepository.saveAndFlush(convB);

        com.ecourtier.domain.ia.entity.MessageIAEntity msgB = com.ecourtier.domain.ia.entity.MessageIAEntity.builder()
                .conversation(convB)
                .role(com.ecourtier.domain.ia.entity.RoleMessageIA.ASSISTANT)
                .contenu("J'ai bien analysé votre demande. Malheureusement, aucune annonce n'a été trouvée.")
                .nombreAnnoncesSuggerees(0)
                .dateEnvoi(LocalDateTime.now())
                .build();
        messageIARepository.saveAndFlush(msgB);

        AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques("30j");

        assertNotNull(stats);
        assertNotNull(stats.getAssistantIA());
        assertEquals(2L, stats.getAssistantIA().getNombreConversations());
        assertEquals(50.0, stats.getAssistantIA().getTauxConversationsShortlistNonVide());
    }

    @Test
    @DisplayName("ANO-03 : KPI financiers - calcul réel des montants propriétaires (caution + 1er loyer), frais conservés, baux signés indépendants du séquestre et filtrage temporel")
    void testKpiFinanciersAno03() {
        UtilisateurEntity locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Sow")
                .prenom("Fatou")
                .email("fatou.sow@ecourtier.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 111 22 33")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());

        AnnonceEntity annonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Plateau")
                .description("Bel appartement")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(250000))
                .superficie(75.0)
                .nombrePieces(3)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.VERIFIEE)
                .utilisateur(testUser)
                .dateCreation(LocalDateTime.now())
                .build());

        // 1. Transaction LIBERE dans la période : caution 100 000, 1er loyer 50 000, frais 5 000
        transactionRepository.save(TransactionEntity.builder()
                .locataire(locataire)
                .proprietaire(testUser)
                .annonce(annonce)
                .montantCaution(BigDecimal.valueOf(100000))
                .montantPremierLoyer(BigDecimal.valueOf(50000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .dateLiberation(LocalDateTime.now().minusDays(5))
                .dateCreation(LocalDateTime.now().minusDays(10))
                .dossierActif(true)
                .build());

        // 2. Transaction BLOQUE : caution 200 000, 1er loyer 100 000, frais 5 000 (ne doit pas entrer dans montants libérés)
        transactionRepository.save(TransactionEntity.builder()
                .locataire(locataire)
                .proprietaire(testUser)
                .annonce(annonce)
                .montantCaution(BigDecimal.valueOf(200000))
                .montantPremierLoyer(BigDecimal.valueOf(100000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .statutBail(StatutBail.SIGNE_LOCATAIRE)
                .dateCreation(LocalDateTime.now().minusDays(4))
                .dossierActif(true)
                .build());

        // 3. Transaction INITIALISE : caution 80 000, 1er loyer 40 000, frais 5 000 (ne doit pas entrer dans montants libérés)
        transactionRepository.save(TransactionEntity.builder()
                .locataire(locataire)
                .proprietaire(testUser)
                .annonce(annonce)
                .montantCaution(BigDecimal.valueOf(80000))
                .montantPremierLoyer(BigDecimal.valueOf(40000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.INITIALISE)
                .statutBail(StatutBail.NON_SIGNE)
                .dateCreation(LocalDateTime.now().minusDays(3))
                .dossierActif(true)
                .build());

        // 4. Transaction REMBOURSE : caution 120 000, 1er loyer 60 000, frais 5 000 (ne doit pas entrer dans montants libérés)
        transactionRepository.save(TransactionEntity.builder()
                .locataire(locataire)
                .proprietaire(testUser)
                .annonce(annonce)
                .montantCaution(BigDecimal.valueOf(120000))
                .montantPremierLoyer(BigDecimal.valueOf(60000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.REMBOURSE)
                .statutBail(StatutBail.NON_SIGNE)
                .dateCreation(LocalDateTime.now().minusDays(2))
                .dossierActif(true)
                .build());

        // 5. Transaction BLOQUE avec SIGNE_COMPLET : bail signé même si séquestre non libéré
        transactionRepository.save(TransactionEntity.builder()
                .locataire(locataire)
                .proprietaire(testUser)
                .annonce(annonce)
                .montantCaution(BigDecimal.valueOf(90000))
                .montantPremierLoyer(BigDecimal.valueOf(45000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.BLOQUE)
                .statutBail(StatutBail.SIGNE_COMPLET)
                .dateCreation(LocalDateTime.now().minusDays(1))
                .dossierActif(true)
                .build());

        // 6. Transaction LIBERE HORS PÉRIODE (libérée il y a 45 jours, période demandée "30j")
        transactionRepository.save(TransactionEntity.builder()
                .locataire(locataire)
                .proprietaire(testUser)
                .annonce(annonce)
                .montantCaution(BigDecimal.valueOf(70000))
                .montantPremierLoyer(BigDecimal.valueOf(35000))
                .fraisDossier(BigDecimal.valueOf(5000))
                .statutSequestre(StatutSequestre.LIBERE)
                .statutBail(StatutBail.NON_SIGNE)
                .dateLiberation(LocalDateTime.now().minusDays(45))
                .dossierActif(false)
                .build());

        AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques("30j");

        assertNotNull(stats);
        assertNotNull(stats.getActiviteCommerciale());

        // Montant versé au propriétaire : caution (100 000) + premier loyer (50 000) = 150 000 FCFA
        assertEquals(0, new BigDecimal("150000").compareTo(stats.getActiviteCommerciale().getMontantVerseProprietaires()));

        // Frais de dossier conservés : 5 000 FCFA
        assertEquals(0, new BigDecimal("5000").compareTo(stats.getActiviteCommerciale().getFraisDossierConserves()));

        // Montant total libéré = 150 000 + 5 000 = 155 000 FCFA
        assertEquals(0, new BigDecimal("155000").compareTo(stats.getActiviteCommerciale().getMontantTotalLibere()));

        // Séquestre bloqué : Tx 2 (200k+100k+5k = 305k) + Tx 5 (90k+45k+5k = 140k) = 445 000 FCFA
        assertEquals(0, new BigDecimal("445000").compareTo(stats.getActiviteCommerciale().getMontantTotalSequestreBloque()));

        // Nombre de baux signés complètement : Tx 1 et Tx 5 dans les 30 derniers jours = 2
        assertEquals(2L, stats.getActiviteCommerciale().getNombreBauxSignesComplets());

        // Nombre réel de transactions initiées créées : Tx 1, 2, 3, 4, 5, 6 = 6
        assertEquals(6L, stats.getActiviteCommerciale().getNombreTransactionsInitiees());
    }

    @Test
    @DisplayName("ANO-03 : KPI financiers - aucune transaction (zéro division par zéro, valeurs cohérentes)")
    void testKpiFinanciersAucuneTransaction() {
        AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques("30j");

        assertNotNull(stats);
        assertNotNull(stats.getActiviteCommerciale());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getActiviteCommerciale().getMontantVerseProprietaires()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getActiviteCommerciale().getFraisDossierConserves()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getActiviteCommerciale().getMontantTotalLibere()));
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getActiviteCommerciale().getMontantTotalSequestreBloque()));
        assertEquals(0L, stats.getActiviteCommerciale().getNombreBauxSignesComplets());
        assertEquals(0L, stats.getActiviteCommerciale().getNombreTransactionsInitiees());
    }

    @Test
    @DisplayName("RES-07 : Calcul statistiques ancré Africa/Dakar / Clock fixe même sous TZ Europe/Paris")
    void testStatistiquesDeterministesSousTimezoneParisEtClockFixe() {
        TimeZone previous = TimeZone.getDefault();
        try {
            if (cacheManager != null && cacheManager.getCache("adminStats") != null) {
                cacheManager.getCache("adminStats").clear();
            }
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
            adminStatistiquesService.setClock(Clock.fixed(Instant.parse("2026-06-15T23:30:00Z"), ZoneId.of("Africa/Dakar")));

            AdminStatistiquesDTO stats = adminStatistiquesService.obtenirStatistiques("7j");
            var series = stats.getUtilisateurs().getNouvellesInscriptions();
            assertEquals(8, series.size());
            assertEquals("2026-06-08", series.get(0).getDate());
            assertEquals("2026-06-15", series.get(series.size() - 1).getDate());
        } finally {
            adminStatistiquesService.setClock(Clock.system(ZoneId.of("Africa/Dakar")));
            if (cacheManager != null && cacheManager.getCache("adminStats") != null) {
                cacheManager.getCache("adminStats").clear();
            }
            TimeZone.setDefault(previous);
        }
    }
}
