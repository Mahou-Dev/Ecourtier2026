package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.ConversationHistoryResponseDTO;
import com.ecourtier.domain.ia.dto.MessageItemDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.entity.MessageIAEntity;
import com.ecourtier.domain.ia.entity.RoleMessageIA;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "ADMIN_BOOTSTRAP_PASSWORD=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@DirtiesContext
@EnabledIfSystemProperty(named = "ecourtier.test.pg16", matches = "true")
@DisplayName("REV-03 PostgreSQL 16 : Verrou pessimiste FOR UPDATE & contrainte unique uq_messages_ia_conversation_ordre (V22)")
class Postgres16IaConcurrencyIntegrationTest {

    static {
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.44");
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ecourtier_ia_pg16_concurrency")
            .withUsername("ecourtier_test")
            .withPassword("ecourtier_test_pwd");

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private IAService iaService;

    @Autowired
    private ConversationIARepository conversationIARepository;

    @Autowired
    private MessageIARepository messageIARepository;

    @Test
    @DisplayName("PG16 : 4 requêtes simultanées sur une même conversation -> sérialisation FOR UPDATE & ordres uniques 1..8")
    void testConcurrentWritesOnPostgres16() throws Exception {
        ConversationCreateResponseDTO conv = iaService.creerConversation(null);
        UUID convId = conv.getConversationId();
        String token = conv.getSessionToken();

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try {
                    iaService.envoyerMessage(
                            convId,
                            new MessageUserRequestDTO("Requête PG16 #" + idx + " appartement Dakar"),
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
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        for (Future<Throwable> f : futures) {
            assertNull(f.get(), "Toutes les écritures concurrentes sur PostgreSQL 16 doivent réussir sans collision");
        }

        ConversationHistoryResponseDTO history = iaService.obtenirHistorique(convId, null, token);
        List<MessageItemDTO> msgs = history.getMessages();
        assertEquals(8, msgs.size());
        Set<Long> ordres = new HashSet<>();
        for (int i = 0; i < msgs.size(); i++) {
            assertEquals(i + 1L, msgs.get(i).getOrdre());
            assertTrue(ordres.add(msgs.get(i).getOrdre()));
        }

        // Vérification directe de la contrainte SQL unique V22 sur PostgreSQL 16
        ConversationIAEntity convEntity = conversationIARepository.findById(convId).orElseThrow();
        assertThrows(DataIntegrityViolationException.class, () ->
                messageIARepository.saveAndFlush(MessageIAEntity.builder()
                        .conversation(convEntity)
                        .role(RoleMessageIA.UTILISATEUR)
                        .ordre(1L)
                        .contenu("Collision SQL directe sur PG16")
                        .dateEnvoi(LocalDateTime.now())
                        .build())
        );
    }
}
