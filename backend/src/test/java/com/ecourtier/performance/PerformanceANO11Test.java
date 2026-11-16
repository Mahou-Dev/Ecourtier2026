package com.ecourtier.performance;

import com.ecourtier.domain.annonce.dto.AnnonceCreateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.*;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.repository.PhotoAnnonceRepository;
import com.ecourtier.domain.annonce.service.AnnonceService;
import com.ecourtier.domain.favori.entity.FavoriEntity;
import com.ecourtier.domain.favori.repository.FavoriRepository;
import com.ecourtier.domain.favori.service.FavoriService;
import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.entity.ConversationChatEntity;
import com.ecourtier.domain.messagerie.entity.MessageChatEntity;
import com.ecourtier.domain.messagerie.repository.ConversationChatRepository;
import com.ecourtier.domain.messagerie.repository.MessageChatRepository;
import com.ecourtier.domain.messagerie.service.MessagerieService;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class PerformanceANO11Test {

    @Autowired
    private AnnonceService annonceService;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private PhotoAnnonceRepository photoAnnonceRepository;

    @Autowired
    private FavoriService favoriService;

    @Autowired
    private FavoriRepository favoriRepository;

    @Autowired
    private MessagerieService messagerieService;

    @Autowired
    private ConversationChatRepository conversationChatRepository;

    @Autowired
    private MessageChatRepository messageChatRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private com.ecourtier.testsupport.TestDatabaseCleaner testDatabaseCleaner;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Statistics statistics;
    private TransactionTemplate transactionTemplate;

    private UtilisateurEntity testLocataire;
    private UtilisateurEntity testProprietaire;
    private List<AnnonceEntity> sampleAnnonces = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        testDatabaseCleaner.cleanAll();
    }

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        transactionTemplate = new TransactionTemplate(transactionManager);
        testDatabaseCleaner.cleanAll();

        transactionTemplate.execute(status -> {
            testLocataire = utilisateurRepository.save(UtilisateurEntity.builder()
                    .nom("Locataire")
                    .prenom("Moussa")
                    .email("locataire.perf@ecourtier.sn")
                    .motDePasse("Secret123!")
                    .telephone("+221770001122")
                    .role(Role.LOCATAIRE)
                    .actif(true)
                    .build());

            testProprietaire = utilisateurRepository.save(UtilisateurEntity.builder()
                    .nom("Proprietaire")
                    .prenom("Fatou")
                    .email("proprio.perf@ecourtier.sn")
                    .motDePasse("Secret123!")
                    .telephone("+221770003344")
                    .role(Role.PROPRIETAIRE)
                    .actif(true)
                    .build());

            sampleAnnonces.clear();
            for (int i = 1; i <= 10; i++) {
                UtilisateurEntity author = (i % 2 == 0) ? testProprietaire :
                        utilisateurRepository.save(UtilisateurEntity.builder()
                                .nom("Auteur" + i)
                                .prenom("Prenom" + i)
                                .email("auteur" + i + "@ecourtier.sn")
                                .motDePasse("Secret123!")
                                .telephone("+22177000" + (1000 + i))
                                .role(Role.PROPRIETAIRE)
                                .actif(true)
                                .build());

                AnnonceEntity annonce = AnnonceEntity.builder()
                        .titre("Annonce Perf #" + i)
                        .description("Description performance pour annonce #" + i)
                        .type(TypeBien.APPARTEMENT)
                        .modeTransaction(ModeTransaction.LOCATION)
                        .prix(BigDecimal.valueOf(200000 + (i * 10000)))
                        .superficie(75.0 + i)
                        .nombrePieces(3)
                        .quartier("Mermoz")
                        .ville("Dakar")
                        .latitude(14.71)
                        .longitude(-17.47)
                        .meuble(true)
                        .animauxAcceptes(false)
                        .statutPublication(StatutPublication.DISPONIBLE)
                        .statutVerification(StatutVerification.VERIFIEE)
                        .dateVerification(LocalDateTime.now())
                        .utilisateur(author)
                        .equipements(Set.of(Equipement.CLIMATISATION, Equipement.WIFI))
                        .proximites(Set.of(ProximiteTag.COMMERCE, ProximiteTag.TRANSPORT))
                        .build();

                AnnonceEntity saved = annonceRepository.save(annonce);

                // Add 2 photos per annonce
                for (int p = 1; p <= 2; p++) {
                    PhotoAnnonceEntity photo = PhotoAnnonceEntity.builder()
                            .annonce(saved)
                            .url("https://photos.ecourtier.sn/a" + i + "/p" + p + ".jpg")
                            .ordre(p)
                            .build();
                    photoAnnonceRepository.save(photo);
                }

                sampleAnnonces.add(saved);
            }
            return null;
        });
    }

    @Test
    @DisplayName("Mesure SQL: Recherche d'annonces paginée (10 annonces)")
    void mesurerRechercheAnnonces() {
        statistics.clear();

        Page<AnnonceResponseDTO> page = transactionTemplate.execute(status ->
                annonceService.rechercherAnnonces(
                        PageRequest.of(0, 10),
                        null, null, (TypeBien) null, null, null,
                        null, null, null, null, null,
                        null, null, false, null, "locataire.perf@ecourtier.sn"
                )
        );

        assertNotNull(page);
        assertEquals(10, page.getContent().size());

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=== [MESURE ANO-11] Recherche Annonces (10 items): " + queryCount + " requêtes préparées ===");
    }

    @Test
    @DisplayName("Mesure SQL: Lister Favoris paginés (10 favoris)")
    void mesurerListerFavoris() {
        // Setup 10 favoris
        transactionTemplate.execute(status -> {
            for (AnnonceEntity a : sampleAnnonces) {
                favoriRepository.save(FavoriEntity.builder()
                        .utilisateur(testLocataire)
                        .annonce(a)
                        .build());
            }
            return null;
        });

        statistics.clear();

        Page<AnnonceResponseDTO> favoris = transactionTemplate.execute(status ->
                favoriService.listerFavoris("locataire.perf@ecourtier.sn", PageRequest.of(0, 10))
        );

        assertNotNull(favoris);
        assertEquals(10, favoris.getContent().size());

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=== [MESURE ANO-11] Lister Favoris (10 items): " + queryCount + " requêtes préparées ===");
    }

    @Test
    @DisplayName("Mesure SQL: Lister Conversations (5 conversations)")
    void mesurerListerConversations() {
        // Setup 5 conversations avec 3 messages chacune
        transactionTemplate.execute(status -> {
            for (int i = 0; i < 5; i++) {
                AnnonceEntity annonce = sampleAnnonces.get(i);
                ConversationChatEntity conv = conversationChatRepository.save(ConversationChatEntity.builder()
                        .annonce(annonce)
                        .locataire(testLocataire)
                        .proprietaire(annonce.getUtilisateur())
                        .dateCreation(LocalDateTime.now().minusHours(i))
                        .build());

                for (int m = 1; m <= 3; m++) {
                    messageChatRepository.save(MessageChatEntity.builder()
                            .conversation(conv)
                            .expediteur(m % 2 == 1 ? testLocataire : annonce.getUtilisateur())
                            .contenu("Message " + m + " dans conv " + i)
                            .dateEnvoi(LocalDateTime.now().minusMinutes(m * 10))
                            .lu(m == 1) // 2 non lus
                            .build());
                }
            }
            return null;
        });

        statistics.clear();

        List<ConversationResponseDTO> conversations = transactionTemplate.execute(status ->
                messagerieService.listerMesConversations("locataire.perf@ecourtier.sn")
        );

        assertNotNull(conversations);
        assertEquals(5, conversations.size());

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=== [MESURE ANO-11] Lister Conversations (5 convs): " + queryCount + " requêtes préparées ===");
    }

    @Test
    @DisplayName("Mesure SQL: Lister Messages d'une conversation (15 messages)")
    void mesurerListerMessages() {
        final UUID[] convIdHolder = new UUID[1];
        transactionTemplate.execute(status -> {
            AnnonceEntity annonce = sampleAnnonces.get(0);
            ConversationChatEntity conv = conversationChatRepository.save(ConversationChatEntity.builder()
                    .annonce(annonce)
                    .locataire(testLocataire)
                    .proprietaire(annonce.getUtilisateur())
                    .dateCreation(LocalDateTime.now())
                    .build());
            convIdHolder[0] = conv.getId();

            for (int m = 1; m <= 15; m++) {
                messageChatRepository.save(MessageChatEntity.builder()
                        .conversation(conv)
                        .expediteur(m % 2 == 1 ? testLocataire : annonce.getUtilisateur())
                        .contenu("Message de test numéro " + m)
                        .dateEnvoi(LocalDateTime.now().minusMinutes(30 - m))
                        .lu(true)
                        .build());
            }
            return null;
        });

        statistics.clear();

        Page<MessageResponseDTO> messages = transactionTemplate.execute(status ->
                messagerieService.listerMessages(convIdHolder[0], PageRequest.of(0, 15), "locataire.perf@ecourtier.sn")
        );

        assertNotNull(messages);
        assertEquals(15, messages.getContent().size());

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=== [MESURE ANO-11] Lister Messages (15 messages): " + queryCount + " requêtes préparées ===");
    }
}
