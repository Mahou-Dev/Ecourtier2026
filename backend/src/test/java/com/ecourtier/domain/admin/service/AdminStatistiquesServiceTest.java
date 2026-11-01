package com.ecourtier.domain.admin.service;

import com.ecourtier.domain.admin.dto.AdminStatistiquesDTO;
import com.ecourtier.domain.admin.repository.AdminStatistiquesRepository;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
}
