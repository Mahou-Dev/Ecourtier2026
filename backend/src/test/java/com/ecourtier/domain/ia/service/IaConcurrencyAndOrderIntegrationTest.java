package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.ConversationHistoryResponseDTO;
import com.ecourtier.domain.ia.dto.MessageItemDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.entity.MessageIAEntity;
import com.ecourtier.domain.ia.entity.RoleMessageIA;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.ecourtier.testsupport.TestDatabaseCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("REV-03 : Concurrence sur l'ordre des messages, contrainte unique et rollback")
class IaConcurrencyAndOrderIntegrationTest {

    @Autowired
    private IAService iaService;

    @Autowired
    private ConversationIARepository conversationIARepository;

    @Autowired
    private MessageIARepository messageIARepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private AnnonceMapper annonceMapper;

    @Autowired
    private LlmExtractionService llmExtractionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private IaRateLimiterService iaRateLimiterService;

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    @BeforeEach
    void setUp() {
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();
    }

    @AfterEach
    void tearDown() {
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();
    }

    private void runConcurrentBurstAndVerifyOrder(int numThreads) throws Exception {
        ConversationCreateResponseDTO conv = iaService.creerConversation(null);
        UUID convId = conv.getConversationId();
        String token = conv.getSessionToken();

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch ready = new CountDownLatch(numThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    iaService.envoyerMessage(
                            convId,
                            new MessageUserRequestDTO("Message concurrent #" + idx + " studio Dakar"),
                            null,
                            token
                    );
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        for (Future<Throwable> f : futures) {
            assertNull(f.get(), "Aucune requête concurrente légitime ne doit échouer sous verrou pessimiste");
        }

        ConversationHistoryResponseDTO history = iaService.obtenirHistorique(convId, null, token);
        List<MessageItemDTO> items = history.getMessages();
        assertEquals(numThreads * 2, items.size(), "Tous les échanges (user + assistant) doivent être persistés");

        Set<Long> ordres = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            MessageItemDTO item = items.get(i);
            long expectedOrdre = i + 1L;
            assertEquals(expectedOrdre, item.getOrdre(), "Ordre strictement séquentiel attendu");
            assertTrue(ordres.add(item.getOrdre()), "Aucun doublon d'ordre permis");
            RoleMessageIA expectedRole = (i % 2 == 0) ? RoleMessageIA.UTILISATEUR : RoleMessageIA.ASSISTANT;
            assertEquals(expectedRole, item.getRole(),
                    "Chaque message utilisateur doit précéder immédiatement sa réponse assistant");
        }
    }

    @Test
    @DisplayName("1. Deux requêtes concurrentes sur la même conversation -> aucune collision d'ordre")
    void testDeuxRequetesConcurrentesMemeConversation() throws Exception {
        runConcurrentBurstAndVerifyOrder(2);
    }

    @Test
    @DisplayName("2. Cinq requêtes simultanées sur la même conversation -> ordre strictement séquentiel 1..10")
    void testCinqRequetesSimultaneesMemeConversation() throws Exception {
        runConcurrentBurstAndVerifyOrder(5);
    }

    @Test
    @DisplayName("3. Requêtes simultanées sur des conversations distinctes -> isolation et ordre indépendant")
    void testRequetesSimultaneesConversationsDistinctes() throws Exception {
        int convCount = 3;
        int msgsPerConv = 2;
        List<ConversationCreateResponseDTO> convs = new ArrayList<>();
        for (int c = 0; c < convCount; c++) {
            convs.add(iaService.creerConversation(null));
        }

        int totalTasks = convCount * msgsPerConv;
        ExecutorService pool = Executors.newFixedThreadPool(totalTasks);
        CountDownLatch ready = new CountDownLatch(totalTasks);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Throwable>> callables = new ArrayList<>();

        for (int c = 0; c < convCount; c++) {
            ConversationCreateResponseDTO conv = convs.get(c);
            for (int m = 0; m < msgsPerConv; m++) {
                final int msgIdx = m;
                callables.add(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    try {
                        iaService.envoyerMessage(
                                conv.getConversationId(),
                                new MessageUserRequestDTO("Appartement Dakar lot " + msgIdx),
                                null,
                                conv.getSessionToken()
                        );
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                });
            }
        }

        List<Future<Throwable>> futures = new ArrayList<>();
        for (Callable<Throwable> task : callables) {
            futures.add(pool.submit(task));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        for (Future<Throwable> f : futures) {
            assertNull(f.get());
        }

        for (ConversationCreateResponseDTO conv : convs) {
            ConversationHistoryResponseDTO hist = iaService.obtenirHistorique(conv.getConversationId(), null, conv.getSessionToken());
            assertEquals(4, hist.getMessages().size());
            assertEquals(List.of(1L, 2L, 3L, 4L),
                    hist.getMessages().stream().map(MessageItemDTO::getOrdre).toList());
        }
    }

    @Test
    @DisplayName("4. Contrainte SQL UNIQUE (conversation_id, ordre) rejette tout doublon en base")
    void testContrainteUniqueConversationOrdre() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(null);
        ConversationIAEntity entity = conversationIARepository.findById(conv.getConversationId()).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        messageIARepository.saveAndFlush(MessageIAEntity.builder()
                .conversation(entity)
                .role(RoleMessageIA.UTILISATEUR)
                .ordre(1L)
                .contenu("Premier message")
                .dateEnvoi(now)
                .build());

        assertThrows(DataIntegrityViolationException.class, () ->
                messageIARepository.saveAndFlush(MessageIAEntity.builder()
                        .conversation(entity)
                        .role(RoleMessageIA.ASSISTANT)
                        .ordre(1L)
                        .contenu("Collision volontaire ordre 1")
                        .dateEnvoi(now)
                        .build()),
                "La contrainte unique uq_messages_ia_conversation_ordre doit bloquer tout doublon d'ordre"
        );
    }

    @Test
    @DisplayName("5. Échec pendant la transaction d'écriture -> rollback complet (aucun message utilisateur orphelin)")
    void testRollbackSiEchecPendantTransactionEcriture() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(null);
        AtomicInteger saveCalls = new AtomicInteger(0);

        MessageIARepository failingOnAssistantRepo = (MessageIARepository) Proxy.newProxyInstance(
                MessageIARepository.class.getClassLoader(),
                new Class<?>[]{MessageIARepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName()) && saveCalls.incrementAndGet() == 2) {
                        throw new IllegalStateException("Panne simulée lors de l'enregistrement du message assistant");
                    }
                    try {
                        return method.invoke(messageIARepository, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );

        IAService rollbackService = new IAService(
                conversationIARepository,
                failingOnAssistantRepo,
                utilisateurRepository,
                annonceRepository,
                annonceMapper,
                llmExtractionService,
                objectMapper,
                transactionManager
        );

        assertThrows(IllegalStateException.class, () ->
                rollbackService.envoyerMessage(
                        conv.getConversationId(),
                        new MessageUserRequestDTO("Appartement à louer Plateau"),
                        null,
                        conv.getSessionToken()
                )
        );

        assertEquals(0L, messageIARepository.countByConversationId(conv.getConversationId()),
                "Le rollback de executeInWriteTx doit annuler l'insertion du message utilisateur");
    }

    @Test
    @DisplayName("6. Conflit PessimisticLock / DataIntegrityViolation traduit proprement en HTTP 409 CONFLICT")
    void testTraductionConflitEnHttp409() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(null);

        MessageIARepository duplicateThrowingRepo = (MessageIARepository) Proxy.newProxyInstance(
                MessageIARepository.class.getClassLoader(),
                new Class<?>[]{MessageIARepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        throw new DataIntegrityViolationException("Simulated unique constraint violation");
                    }
                    try {
                        return method.invoke(messageIARepository, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );

        IAService conflictService = new IAService(
                conversationIARepository,
                duplicateThrowingRepo,
                utilisateurRepository,
                annonceRepository,
                annonceMapper,
                llmExtractionService,
                objectMapper,
                transactionManager
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                conflictService.envoyerMessage(
                        conv.getConversationId(),
                        new MessageUserRequestDTO("Studio Almadies"),
                        null,
                        conv.getSessionToken()
                )
        );
        assertEquals(409, ex.getStatusCode().value());
    }
}
