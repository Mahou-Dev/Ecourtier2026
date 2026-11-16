package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.MessageIaResponseDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.entity.MessageIAEntity;
import com.ecourtier.domain.ia.entity.RoleMessageIA;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IAServiceMultiTourAno07IntegrationTest {

    @Autowired
    private IAService iaService;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private ConversationIARepository conversationIARepository;

    @Autowired
    private MessageIARepository messageIARepository;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity proprietaire;
    private UtilisateurEntity locataire;

    @AfterEach
    void tearDown() {
        testDatabaseCleaner.cleanAll();
    }

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();

        proprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Diop")
                .prenom("Abdou")
                .email("abdou.diop@ecourtier.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 222 33 44")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Awa")
                .email("awa.ndiaye@ecourtier.sn")
                .motDePasse("Password123!")
                .telephone("+221 77 333 44 55")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());
    }

    @Test
    @DisplayName("ANO-07 : Scénario multi-tours complet avec persistance cumulative et règle chambres vs pièces")
    void testScenarioMultiToursComplet() {
        // Création de la conversation
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        UUID convId = conv.getConversationId();

        // Tour 1 : « Je cherche un appartement à louer à Dakar. »
        MessageIaResponseDTO r1 = iaService.envoyerMessage(convId,
                new MessageUserRequestDTO("Je cherche un appartement à louer à Dakar."),
                locataire.getEmail());

        assertNotNull(r1.getCriteresExtraits());
        assertEquals(TypeBien.APPARTEMENT, r1.getCriteresExtraits().getType());
        assertEquals(ModeTransaction.LOCATION, r1.getCriteresExtraits().getModeTransaction());
        assertEquals("Dakar", r1.getCriteresExtraits().getVille());
        assertNotNull(r1.getQuestionDeRelance(), "La question de relance doit être présente (budget manquant)");

        // Tour 2 : « Mon budget est de 250 000 FCFA. »
        MessageIaResponseDTO r2 = iaService.envoyerMessage(convId,
                new MessageUserRequestDTO("Mon budget est de 250 000 FCFA."),
                locataire.getEmail());

        assertNotNull(r2.getCriteresExtraits());
        assertEquals(0, new BigDecimal("250000").compareTo(r2.getCriteresExtraits().getBudgetMax()));
        assertEquals(TypeBien.APPARTEMENT, r2.getCriteresExtraits().getType(), "Type appartement conservé");
        assertEquals(ModeTransaction.LOCATION, r2.getCriteresExtraits().getModeTransaction(), "Mode transaction conservé");
        assertEquals("Dakar", r2.getCriteresExtraits().getVille(), "Ville Dakar conservée");
        assertNull(r2.getQuestionDeRelance(), "La question sur le budget doit être résolue");

        // Tour 3 : « Je veux au moins deux chambres, au deuxième étage. »
        MessageIaResponseDTO r3 = iaService.envoyerMessage(convId,
                new MessageUserRequestDTO("Je veux au moins deux chambres, au deuxième étage."),
                locataire.getEmail());

        assertNotNull(r3.getCriteresExtraits());
        assertEquals(2, r3.getCriteresExtraits().getEtage(), "Étage 2 extrait");
        // Conformité Règle D : pas de colonne chambre -> nombrePieces ne doit PAS être assimilé à 2
        assertNull(r3.getCriteresExtraits().getNombrePieces(), "Ne pas assimiler arbitrairement chambres et pièces");
        assertEquals(0, new BigDecimal("250000").compareTo(r3.getCriteresExtraits().getBudgetMax()), "Budget conservé");
        assertEquals(TypeBien.APPARTEMENT, r3.getCriteresExtraits().getType(), "Type conservé");
        assertEquals("Dakar", r3.getCriteresExtraits().getVille(), "Ville conservée");

        // Tour 4 : « J'ai un chat et je souhaite au moins 70 m². »
        MessageIaResponseDTO r4 = iaService.envoyerMessage(convId,
                new MessageUserRequestDTO("J'ai un chat et je souhaite au moins 70 m²."),
                locataire.getEmail());

        assertNotNull(r4.getCriteresExtraits());
        assertEquals(Boolean.TRUE, r4.getCriteresExtraits().getAnimauxAcceptes(), "Chat détecté -> animauxAcceptes true");
        assertEquals(70.0, r4.getCriteresExtraits().getSuperficieMin(), "Superficie min 70 m² extraite");
        assertEquals(2, r4.getCriteresExtraits().getEtage(), "Étage 2 conservé");
        assertEquals(0, new BigDecimal("250000").compareTo(r4.getCriteresExtraits().getBudgetMax()), "Budget conservé");
        assertEquals(TypeBien.APPARTEMENT, r4.getCriteresExtraits().getType(), "Type conservé");
        assertEquals("Dakar", r4.getCriteresExtraits().getVille(), "Ville conservée");
    }

    @Test
    @DisplayName("ANO-07 : Remplacement d'une valeur de budget existante par une nouvelle valeur et conservation")
    void testRemplacementBudgetEtConservation() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        UUID convId = conv.getConversationId();

        // Message 1 : budget initial 200 000
        iaService.envoyerMessage(convId, new MessageUserRequestDTO("Appartement à Dakar budget 200000 FCFA"), locataire.getEmail());

        // Message 2 : augmentation du budget à 350 000
        MessageIaResponseDTO r2 = iaService.envoyerMessage(convId, new MessageUserRequestDTO("Finalement mon budget max est de 350 000 FCFA"), locataire.getEmail());
        assertEquals(0, new BigDecimal("350000").compareTo(r2.getCriteresExtraits().getBudgetMax()), "Le nouveau budget doit remplacer l'ancien");
        assertEquals("Dakar", r2.getCriteresExtraits().getVille(), "La ville antérieure doit être conservée");

        // Message 3 : précision sans mention de budget
        MessageIaResponseDTO r3 = iaService.envoyerMessage(convId, new MessageUserRequestDTO("avec balcon au deuxième étage"), locataire.getEmail());
        assertEquals(0, new BigDecimal("350000").compareTo(r3.getCriteresExtraits().getBudgetMax()), "Le budget de 350 000 doit rester conservé");
        assertEquals(2, r3.getCriteresExtraits().getEtage(), "Étage 2 extrait");
    }

    @Test
    @DisplayName("ANO-07 : Question de clarification portant sur un critère autre que le budget reste présente si seul le budget est renseigné")
    void testQuestionClarificationAutreQueBudgetConservee() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        UUID convId = conv.getConversationId();

        // Enregistrer manuellement un message précédent ayant une question sur le quartier
        ConversationIAEntity convEntity = conversationIARepository.findById(convId).orElseThrow();
        MessageIAEntity msgPrecedent = MessageIAEntity.builder()
                .conversation(convEntity)
                .role(RoleMessageIA.ASSISTANT)
                .contenu("Pour affiner, dans quel quartier de Dakar cherchez-vous ?")
                .criteresExtraits("{\"ville\":\"Dakar\",\"type\":\"APPARTEMENT\",\"questionDeRelance\":\"Dans quel quartier recherchez-vous ?\"}")
                .dateEnvoi(LocalDateTime.now().minusMinutes(2))
                .build();
        messageIARepository.save(msgPrecedent);

        // Utilisateur répond uniquement avec le budget
        MessageIaResponseDTO rep = iaService.envoyerMessage(convId,
                new MessageUserRequestDTO("Mon budget est de 250 000 FCFA"),
                locataire.getEmail());

        // La question portait sur le quartier. Comme le quartier n'est toujours pas résolu, la question doit rester présente
        assertNotNull(rep.getQuestionDeRelance(), "La question sur le quartier ne doit PAS être supprimée quand seul le budget est renseigné");
        assertTrue(rep.getQuestionDeRelance().contains("quartier"));
    }

    @Test
    @DisplayName("ANO-07 : Recommandations - exclusions strictes (EN_ATTENTE, REJETEE, RESERVEE, mauvaise superficie ou mauvais étage) et acceptation (VERIFIEE et DISPONIBLE)")
    void testRecommandationsFiltrageStrict() {
        // Annonce 1 : DISPONIBLE, VERIFIEE, 2ème étage, 80 m², 200 000 FCFA -> DOIT ÊTRE RECOMMANDÉE
        AnnonceEntity aConforme = annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Conforme Plateau")
                .description("Bel appartement conforme")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(80.0)
                .nombrePieces(3)
                .etage(2)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.VERIFIEE)
                .utilisateur(proprietaire)
                .build());

        // Annonce 2 : EN_ATTENTE -> NE DOIT PAS ÊTRE RECOMMANDÉE
        annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement En Attente")
                .description("En attente de vérification")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(80.0)
                .nombrePieces(3)
                .etage(2)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.EN_ATTENTE)
                .utilisateur(proprietaire)
                .build());

        // Annonce 3 : REJETEE -> NE DOIT PAS ÊTRE RECOMMANDÉE
        annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Rejeté")
                .description("Rejeté par la modération")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(80.0)
                .nombrePieces(3)
                .etage(2)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.REJETEE)
                .utilisateur(proprietaire)
                .build());

        // Annonce 4 : RESERVEE -> NE DOIT PAS ÊTRE RECOMMANDÉE
        annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Réservé")
                .description("Déjà réservé")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(80.0)
                .nombrePieces(3)
                .etage(2)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.RESERVEE)
                .statutVerification(StatutVerification.VERIFIEE)
                .utilisateur(proprietaire)
                .build());

        // Annonce 5 : Mauvais étage (étage 1 au lieu de étage 2) -> NE DOIT PAS ÊTRE RECOMMANDÉE
        annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Étage 1")
                .description("Au premier étage")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(80.0)
                .nombrePieces(3)
                .etage(1)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.VERIFIEE)
                .utilisateur(proprietaire)
                .build());

        // Annonce 6 : Superficie insuffisante (50 m² pour minimum 70 m²) -> NE DOIT PAS ÊTRE RECOMMANDÉE
        annonceRepository.save(AnnonceEntity.builder()
                .titre("Appartement Trop Petit")
                .description("50 mètres carrés")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(200000))
                .superficie(50.0)
                .nombrePieces(3)
                .etage(2)
                .quartier("Plateau")
                .ville("Dakar")
                .latitude(14.67)
                .longitude(-17.43)
                .statutPublication(StatutPublication.DISPONIBLE)
                .statutVerification(StatutVerification.VERIFIEE)
                .utilisateur(proprietaire)
                .build());

        // Recherche demandant : appartement à louer, budget 250 000, deuxième étage, au moins 70 m²
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        MessageIaResponseDTO rep = iaService.envoyerMessage(conv.getConversationId(),
                new MessageUserRequestDTO("Appartement à louer pour 250000 FCFA, au deuxième étage et au moins 70 m²"),
                locataire.getEmail());

        assertNotNull(rep.getAnnoncesSuggeres());
        assertEquals(1, rep.getAnnoncesSuggeres().size(), "Seule l'annonce conforme (VERIFIEE, DISPONIBLE, étage 2, >=70m²) doit être suggérée");
        assertEquals(aConforme.getId(), rep.getAnnoncesSuggeres().get(0).getId());
    }

    @Test
    @DisplayName("ANO-05 (LOT 1) : Contrôle d'accès à la conversation préservé (403 si utilisateur non propriétaire de la conversation)")
    void testControleAccesLot1Preserve() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        UUID convId = conv.getConversationId();

        // Tentative d'accès par un tiers
        assertThrows(ResponseStatusException.class, () ->
                iaService.envoyerMessage(convId,
                        new MessageUserRequestDTO("Tentative d'accès non autorisée"),
                        proprietaire.getEmail()));

        // Tentative d'accès anonyme
        assertThrows(ResponseStatusException.class, () ->
                iaService.envoyerMessage(convId,
                        new MessageUserRequestDTO("Tentative d'accès anonyme"),
                        null));
    }
}
