package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.ConversationHistoryResponseDTO;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.ecourtier.domain.ia.dto.MessageIaResponseDTO;
import com.ecourtier.domain.ia.dto.MessageItemDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.entity.MessageIAEntity;
import com.ecourtier.domain.ia.entity.RoleMessageIA;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.ecourtier.testsupport.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Conformité IA RENDER-GEMINI-06 — ANO-PROD-03 à ANO-PROD-11")
class ComplianceAno03To11IntegrationTest {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    @Autowired
    private IAService iaService;

    @Autowired
    private RuleBasedCriteriaExtractor extractor;

    @Autowired
    private ConversationIARepository conversationIARepository;

    @Autowired
    private MessageIARepository messageIARepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private IaRateLimiterService iaRateLimiterService;

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    private UtilisateurEntity bailleur;
    private UtilisateurEntity locataire;

    @BeforeEach
    void setUp() {
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();

        bailleur = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Ndiaye")
                .prenom("Cheikh")
                .email("cheikh.bailleur@compliance.sn")
                .motDePasse("Password123!")
                .telephone("+221771010101")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        locataire = utilisateurRepository.save(UtilisateurEntity.builder()
                .nom("Fall")
                .prenom("Aminata")
                .email("aminata.locataire@compliance.sn")
                .motDePasse("Password123!")
                .telephone("+221772020202")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        iaService.setClock(Clock.system(DAKAR_ZONE));
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();
    }

    @Test
    @DisplayName("ANO-PROD-04 : Ordre chronologique déterministe sous horloge figée (ordre monotonic 1..4) après F5")
    void testAno04_OrdreChronologiqueDeterministeSousHorlogeFigee() {
        iaService.setClock(Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), DAKAR_ZONE));

        ConversationCreateResponseDTO anonConv = iaService.creerConversation(null, null);
        String token = anonConv.getSessionToken();
        assertNotNull(token);

        iaService.envoyerMessage(anonConv.getConversationId(), new MessageUserRequestDTO("Question 1 : Studio à Dakar"), null, token);
        iaService.envoyerMessage(anonConv.getConversationId(), new MessageUserRequestDTO("Question 2 : Budget 150 000 FCFA"), null, token);

        ConversationHistoryResponseDTO history = iaService.obtenirHistorique(anonConv.getConversationId(), null, token);
        List<MessageItemDTO> msgs = history.getMessages();
        assertEquals(4, msgs.size());

        assertEquals(RoleMessageIA.UTILISATEUR, msgs.get(0).getRole());
        assertEquals("Question 1 : Studio à Dakar", msgs.get(0).getContenu());
        assertEquals(1L, msgs.get(0).getOrdre());

        assertEquals(RoleMessageIA.ASSISTANT, msgs.get(1).getRole());
        assertEquals(2L, msgs.get(1).getOrdre());

        assertEquals(RoleMessageIA.UTILISATEUR, msgs.get(2).getRole());
        assertEquals("Question 2 : Budget 150 000 FCFA", msgs.get(2).getContenu());
        assertEquals(3L, msgs.get(2).getOrdre());

        assertEquals(RoleMessageIA.ASSISTANT, msgs.get(3).getRole());
        assertEquals(4L, msgs.get(3).getOrdre());

        List<MessageIAEntity> entityOrder = messageIARepository
                .findByConversationIdOrderByOrdreAscDateEnvoiAscIdAsc(anonConv.getConversationId());
        assertEquals(List.of(1L, 2L, 3L, 4L), entityOrder.stream().map(MessageIAEntity::getOrdre).toList());
    }

    @Test
    @DisplayName("ANO-PROD-04 : Compatibilité ascendante avec ligne héritée ordre = 0 suivie de nouveaux tours (unicité REV-03 préservée)")
    void testAno04_CompatibiliteLignesHeriteesOrdreZero() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 11, 22, 10, 0, 0);
        ConversationIAEntity conv = conversationIARepository.save(ConversationIAEntity.builder()
                .utilisateur(locataire)
                .dateCreation(sameTime)
                .build());

        // Insertion d'une ligne héritée à ordre=0 (V21/V22 garantissent ensuite l'unicité de (conversation_id, ordre))
        MessageIAEntity legacyZero = messageIARepository.saveAndFlush(MessageIAEntity.builder()
                .conversation(conv)
                .role(RoleMessageIA.UTILISATEUR)
                .ordre(0L)
                .contenu("Question héritée")
                .dateEnvoi(sameTime)
                .build());

        iaService.envoyerMessage(
                conv.getId(),
                new MessageUserRequestDTO("Suite de ma recherche à Dakar"),
                locataire.getEmail()
        );

        List<MessageIAEntity> sorted = messageIARepository
                .findByConversationIdOrderByOrdreAscDateEnvoiAscIdAsc(conv.getId());
        assertEquals(3, sorted.size());
        assertEquals(legacyZero.getId(), sorted.get(0).getId());
        assertEquals(List.of(0L, 1L, 2L), sorted.stream().map(MessageIAEntity::getOrdre).toList());
    }

    @Test
    @DisplayName("ANO-PROD-05 / IA-03 : Toutes les formulations comparatives et négations de type sont correctement résolues")
    void testAno05_ComparatifRemplacementTypeEtNegation() {
        assertEquals(TypeBien.STUDIO, extractor.extractCriteria("Je veux un studio plutôt qu'une chambre à Dakar").getType());
        assertEquals(TypeBien.VILLA, extractor.extractCriteria("Je veux une villa et non un appartement").getType());
        assertEquals(TypeBien.CHAMBRE, extractor.extractCriteria("Pas un studio mais une chambre à Mermoz").getType());
        assertEquals(TypeBien.APPARTEMENT, extractor.extractCriteria("Un appartement au lieu d'un studio").getType());
        assertEquals(TypeBien.VILLA, extractor.extractCriteria("Finalement je préfère une villa plutôt qu'un appartement").getType());
        assertEquals(TypeBien.APPARTEMENT, extractor.extractCriteria("Je préférerais un appartement au lieu d'une chambre à Dakar").getType());

        assertEquals(ModeTransaction.VENTE, extractor.extractCriteria("Acheter plutôt que louer une villa à Saly").getModeTransaction());
        assertEquals(ModeTransaction.LOCATION, extractor.extractCriteria("Louer plutôt qu'acheter un appartement à Dakar").getModeTransaction());

        // Scénario multi-tour : CHAMBRE au tour 1 -> corrigé par comparatif au tour 2
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        MessageIaResponseDTO t1 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Je cherche une chambre à louer à Dakar pour 150 000 FCFA"),
                locataire.getEmail()
        );
        assertEquals(TypeBien.CHAMBRE, t1.getCriteresExtraits().getType());

        MessageIaResponseDTO t2 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Finalement je préfère un studio plutôt qu'une chambre"),
                locataire.getEmail()
        );
        assertEquals(TypeBien.STUDIO, t2.getCriteresExtraits().getType());
        assertEquals("Dakar", t2.getCriteresExtraits().getVille());
        assertEquals(new BigDecimal("150000"), t2.getCriteresExtraits().getBudgetMax());
    }

    @Test
    @DisplayName("ANO-PROD-09 / IA-10 : Questions de fiabilité et vérification juridique répondent sincèrement sans casser IA-08 ni perdre les critères")
    void testAno09_TransparenceJuridiqueEtNonConfusionIa08() {
        List<String> faqFormulations = List.of(
                "Comment être sûr que les annonces sont fiables ?",
                "Comment vérifiez-vous les propriétaires ?",
                "Y a-t-il des arnaques ? Comment vous protégez les locataires ?",
                "Est-ce que je dois payer avant de visiter ?",
                "Quels documents dois-je vérifier avant de signer un bail ?",
                "Comment fonctionne la vérification des annonces et du bail ?"
        );

        for (String q : faqFormulations) {
            CriteresExtraitsDTO criteres = extractor.extractCriteria(q);
            assertEquals("FAQ_VERIFICATION_JURIDIQUE", criteres.getIntentionSpeciale(),
                    "Attendu FAQ_VERIFICATION_JURIDIQUE pour: " + q);
        }

        // Vérifier que IA-08 ("maison fiable et sécurisée à louer à Dakar") n'est PAS déroutée en FAQ juridique
        CriteresExtraitsDTO ia08 = extractor.extractCriteria("Je cherche une maison fiable et sécurisée à louer à Dakar");
        assertNotEquals("FAQ_VERIFICATION_JURIDIQUE", ia08.getIntentionSpeciale());
        assertNotNull(ia08.getVille());

        // Scénario conversationnel avec conservation des critères avant/pendant/après question juridique
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        iaService.envoyerMessage(conv.getConversationId(),
                new MessageUserRequestDTO("Appartement à louer à Mermoz budget 300 000 FCFA"), locataire.getEmail());

        MessageIaResponseDTO repFaq = iaService.envoyerMessage(conv.getConversationId(),
                new MessageUserRequestDTO("Comment être sûr que les annonces sont fiables ?"), locataire.getEmail());

        String textLower = repFaq.getReponse().toLowerCase(Locale.ROOT);
        assertTrue(textLower.contains("verifie") || textLower.contains("vérifiée"), "Doit expliquer VERIFIEE");
        assertTrue(textLower.contains("identité") || textLower.contains("justificatif"), "Doit parler du contrôle bailleur");
        assertTrue(textLower.contains("bail") && textLower.contains("cni"), "Doit citer documents sénégalais usuels");
        assertTrue(textLower.contains("visite physique"), "Doit conseiller visite avant paiement");
        assertTrue(textLower.contains("notaire") && textLower.contains("limites"), "Doit exposer limites et notaire");
        assertFalse(textLower.contains("malheureusement, aucune annonce"), "Ne doit pas lancer de recherche catalogue vide");
        assertEquals("FAQ_VERIFICATION_JURIDIQUE", extractor.extractCriteria("Comment être sûr que les annonces sont fiables ?").getIntentionSpeciale());

        // Les critères précédents (APPARTEMENT, Mermoz, 300000) sont conservés intacts !
        assertEquals(TypeBien.APPARTEMENT, repFaq.getCriteresExtraits().getType());
        assertEquals("Mermoz", repFaq.getCriteresExtraits().getQuartier());
        assertEquals(new BigDecimal("300000"), repFaq.getCriteresExtraits().getBudgetMax());
    }

    @Test
    @DisplayName("ANO-PROD-08 & 11 : Préférences souples non éliminatoires, cuisine équipée, cour/terrain/calme et récapitulatif complet")
    void testAno08Et11_PreferencesSouplesEtCatalogueSynthetique() {
        // 1. "cuisine équipée" ne force plus meuble = true
        CriteresExtraitsDTO cuisineSeule = extractor.extractCriteria("Appartement avec cuisine équipée à Dakar 250 000 FCFA");
        assertNull(cuisineSeule.getMeuble(), "cuisine équipée ne doit pas forcer meuble=true");
        assertNotNull(cuisineSeule.getAttentesComplementaires());
        assertTrue(cuisineSeule.getAttentesComplementaires().stream().anyMatch(s -> s.contains("cuisine")));

        // 2. Préférence souple "je préférerais ... meublé" + recherche catalogue synthétique
        AnnonceEntity nonMeuble = creerAnnonce("Appartement Mermoz classique", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Dakar", "Mermoz", new BigDecimal("260000"), false, StatutVerification.VERIFIEE, StatutPublication.DISPONIBLE,
                "Bel appartement avec cuisine équipée et cour intérieure.");
        AnnonceEntity meuble = creerAnnonce("Appartement Mermoz tout équipé", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Dakar", "Mermoz", new BigDecimal("280000"), true, StatutVerification.VERIFIEE, StatutPublication.DISPONIBLE,
                "Appartement calme et meublé avec cuisine équipée.");
        creerAnnonce("Appartement rejeté Mermoz", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Dakar", "Mermoz", new BigDecimal("200000"), true, StatutVerification.REJETEE, StatutPublication.DISPONIBLE, "Ne doit jamais sortir");
        creerAnnonce("Appartement déjà loué Mermoz", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Dakar", "Mermoz", new BigDecimal("200000"), true, StatutVerification.VERIFIEE, StatutPublication.LOUEE, "Déjà loué");
        creerAnnonce("Appartement en attente Mermoz", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Dakar", "Mermoz", new BigDecimal("200000"), true, StatutVerification.EN_ATTENTE, StatutPublication.DISPONIBLE, "En attente");

        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        MessageIaResponseDTO rep = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Appartement calme à Mermoz Dakar avec cuisine équipée et cour, budget 300 000 FCFA, je préférerais un logement meublé si possible"),
                locataire.getEmail()
        );

        // meuble reste null (non éliminatoire!), préférence souple enregistrée
        assertNull(rep.getCriteresExtraits().getMeuble());
        assertNotNull(rep.getCriteresExtraits().getPreferencesSouples());
        assertTrue(rep.getCriteresExtraits().getPreferencesSouples().stream().anyMatch(p -> p.contains("meubl")));
        assertTrue(rep.getCriteresExtraits().getPreferencesSouples().stream().anyMatch(p -> p.contains("calme")));
        assertTrue(rep.getCriteresExtraits().getAttentesComplementaires().stream().anyMatch(a -> a.contains("cuisine")));
        assertTrue(rep.getCriteresExtraits().getAttentesComplementaires().stream().anyMatch(a -> a.contains("cour")));

        // Seulement les 2 annonces VERIFIEE + DISPONIBLE sont retournées, et l'appartement meublé arrive EN PREMIER !
        List<AnnonceResponseDTO> suggestions = rep.getAnnoncesSuggeres();
        assertEquals(2, suggestions.size());
        assertEquals(meuble.getId(), suggestions.get(0).getId(), "La préférence souple meublé doit favoriser le logement meublé en tête");
        assertEquals(nonMeuble.getId(), suggestions.get(1).getId(), "Le logement non meublé reste éligible car préférence non éliminatoire");

        // Vérification fuite PII (email/téléphone bailleur masqués)
        for (AnnonceResponseDTO a : suggestions) {
            assertNotNull(a.getAuteur());
            assertNull(a.getAuteur().getEmail());
            assertNull(a.getAuteur().getTelephone());
            assertNull(a.getDocumentsVerification());
        }

        // Récapitulatif & FCFA formaté & télémétrie (ANO-PROD-10)
        assertTrue(rep.getReponse().contains("300 000 FCFA"));
        assertTrue(rep.getReponse().contains("cuisine") || rep.getReponse().contains("cour"));
        assertNotNull(rep.getMetriques());
        assertNotNull(rep.getMetriques().getTotalMs());
        assertTrue(rep.getMetriques().getTotalMs() >= 0L);
    }

    @Test
    @DisplayName("ANO-PROD-08 : Demande exploratoire / indécise sur secteur calme ne conclut pas prématurément à 'aucune annonce'")
    void testAno08_DemandeExploratoireSansEchecPremature() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());
        MessageIaResponseDTO rep = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Je cherche un secteur calme mais je ne sais pas encore quelles questions clarifier."),
                locataire.getEmail()
        );

        assertFalse(rep.getReponse().toLowerCase(Locale.ROOT).contains("malheureusement, aucune annonce"),
                "Ne doit pas conclure à 'aucune annonce' avant d'avoir type + budget");
        assertNotNull(rep.getQuestionDeRelance());
        assertNotNull(rep.getCriteresExtraits().getPrecisionsAClarifier());
        assertFalse(rep.getCriteresExtraits().getPrecisionsAClarifier().isEmpty());
    }

    @Test
    @DisplayName("Changement de ville réinitialise quartier/meublé obsolètes et diagnostic propose les alternatives réelles")
    void testChangementVilleEtDiagnosticAlternatives() {
        creerAnnonce("Bel appartement Almadies", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Dakar", "Almadies", new BigDecimal("250000"), true, StatutVerification.VERIFIEE, StatutPublication.DISPONIBLE,
                "Appartement vérifié aux Almadies.");
        creerAnnonce("Appartement Boucotte", TypeBien.APPARTEMENT, ModeTransaction.LOCATION,
                "Ziguinchor", "Boucotte", new BigDecimal("95000"), false, StatutVerification.VERIFIEE, StatutPublication.DISPONIBLE,
                "Appartement non meublé à Boucotte.");
        creerAnnonce("Studio Kenia", TypeBien.STUDIO, ModeTransaction.LOCATION,
                "Ziguinchor", "Kenia", new BigDecimal("80000"), true, StatutVerification.VERIFIEE, StatutPublication.DISPONIBLE,
                "Studio meublé à Kenia.");

        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());

        // Tour 1 : Studio meublé à Dakar (0 match exact à Dakar, mais diagnostic indique les appartements de Dakar + le studio à Ziguinchor)
        MessageIaResponseDTO t1 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Je cherche un studio meublé à Almadies Dakar"),
                locataire.getEmail()
        );
        assertTrue(t1.getAnnoncesSuggeres().isEmpty());
        assertTrue(t1.getReponse().contains("Dakar") && t1.getReponse().contains("Ziguinchor"),
                "Le diagnostic doit mentionner les alternatives réelles à Dakar et le studio à Ziguinchor");

        // Tour 2 : Nouvelle recherche complète dans une autre ville (Boucotte Ziguinchor) -> ne doit pas hériter meuble=true ni Almadies
        MessageIaResponseDTO t2 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Appartement à louer à Boucotte Ziguinchor budget 100000 FCFA"),
                locataire.getEmail()
        );
        assertNull(t2.getCriteresExtraits().getMeuble(), "meuble=true de Dakar ne doit pas bloquer Ziguinchor");
        assertEquals("Boucotte", t2.getCriteresExtraits().getQuartier());
        assertEquals("Ziguinchor", t2.getCriteresExtraits().getVille());
        assertEquals(1, t2.getAnnoncesSuggeres().size());
        assertEquals("Appartement Boucotte", t2.getAnnoncesSuggeres().get(0).getTitre());

        // Tour 3 : Passage simple à Dakar sans quartier -> doit effacer Boucotte au lieu de combiner Dakar + Boucotte
        MessageIaResponseDTO t3 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Et à Dakar budget 300 000 FCFA ?"),
                locataire.getEmail()
        );
        assertNull(t3.getCriteresExtraits().getQuartier(), "Changer de ville sans quartier doit effacer le quartier précédent");
        assertEquals("Dakar", t3.getCriteresExtraits().getVille());
        assertEquals(1, t3.getAnnoncesSuggeres().size());
    }

    @Test
    @DisplayName("NLU Multi-Tours : Demande de biens disponibles dans un nouveau quartier/ville ne propage pas les filtres stricts précédents (pièces/type/budget)")
    void testTransitionVersListeDisponibleSansPollutionCriterePrecedent() {
        // Annonce vérifiée disponible à Kenia (Ziguinchor) : Studio 1 pièce à 80 000 FCFA
        AnnonceEntity studioKenia = annonceRepository.save(AnnonceEntity.builder()
                .titre("Un studio meuble a Kenia/ziguinchor")
                .description("Studio meublé avec commodités à Kenia Ziguinchor")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(new BigDecimal("80000"))
                .superficie(16.0)
                .nombrePieces(1)
                .quartier("Kenia")
                .ville("Ziguinchor")
                .latitude(12.583)
                .longitude(-16.271)
                .meuble(true)
                .animauxAcceptes(false)
                .confirmationAutomatique(true)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .supprime(false)
                .utilisateur(bailleur)
                .build());

        ConversationCreateResponseDTO conv = iaService.creerConversation(locataire.getEmail());

        // Tour 1 : Demande ciblée 3 pièces à Nema (inhabitée ou sans correspondance 3 pièces)
        MessageIaResponseDTO t1 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Appartement 3 pièces à Nema < 160 000 FCFA"),
                locataire.getEmail()
        );
        assertEquals("Nema", t1.getCriteresExtraits().getQuartier());
        assertEquals(3, t1.getCriteresExtraits().getNombrePieces());

        // Tour 2 : Question ouverte sur Kenia
        MessageIaResponseDTO t2 = iaService.envoyerMessage(
                conv.getConversationId(),
                new MessageUserRequestDTO("Bonjour, avez-vous des biens disponibles à Ziguinchor ou à Kenia ?"),
                locataire.getEmail()
        );

        // Vérification que les critères bloquants stricts du tour 1 ne polluent pas la question ouverte
        assertEquals("Ziguinchor", t2.getCriteresExtraits().getVille());
        assertEquals("Kenia", t2.getCriteresExtraits().getQuartier());
        assertNull(t2.getCriteresExtraits().getNombrePieces(), "Le filtre 3 pièces ne doit pas être conservé sur une demande ouverte");
        assertNull(t2.getCriteresExtraits().getBudgetMax(), "Le budget max ne doit pas brider une demande ouverte de biens disponibles");

        // L'annonce du studio à Kenia doit être retournée avec succès !
        assertFalse(t2.getAnnoncesSuggeres().isEmpty(), "Le studio vérifié de Kenia doit être trouvé");
        assertEquals(studioKenia.getId(), t2.getAnnoncesSuggeres().get(0).getId());
        assertTrue(t2.getReponse().contains("Un studio meuble a Kenia/ziguinchor") || t2.getReponse().contains("annonce(s) vérifiée(s)"),
                "La réponse doit présenter le studio disponible");
        assertFalse(t2.getReponse().contains("aucun bien vérifié n'est disponible dans le quartier Kenia"),
                "Ne doit plus diagnostiquer faussement zéro bien à Kenia");
    }

    private AnnonceEntity creerAnnonce(
            String titre,
            TypeBien type,
            ModeTransaction mode,
            String ville,
            String quartier,
            BigDecimal prix,
            boolean meuble,
            StatutVerification verif,
            StatutPublication pub,
            String description
    ) {
        return annonceRepository.save(AnnonceEntity.builder()
                .titre(titre)
                .description(description)
                .type(type)
                .modeTransaction(mode)
                .prix(prix)
                .superficie(85.0)
                .nombrePieces(3)
                .quartier(quartier)
                .ville(ville)
                .latitude(14.716)
                .longitude(-17.467)
                .meuble(meuble)
                .animauxAcceptes(false)
                .confirmationAutomatique(true)
                .statutVerification(verif)
                .statutPublication(pub)
                .supprime(false)
                .utilisateur(bailleur)
                .build());
    }
}
