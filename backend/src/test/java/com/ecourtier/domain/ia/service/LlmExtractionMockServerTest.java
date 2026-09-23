package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmExtractionMockServerTest {

    private LlmExtractionService llmExtractionService;
    private RuleBasedCriteriaExtractor ruleBasedExtractor;
    private HttpServer mockHttpServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        ruleBasedExtractor = new RuleBasedCriteriaExtractor();
        llmExtractionService = new LlmExtractionService(ruleBasedExtractor, new ObjectMapper());

        // Démarrage d'un serveur HTTP local léger pour mocker l'API LLM
        mockHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockHttpServer.getAddress().getPort();
        mockHttpServer.start();

        ReflectionTestUtils.setField(llmExtractionService, "apiKey", "test-api-key-123");
        ReflectionTestUtils.setField(llmExtractionService, "provider", "claude");
        ReflectionTestUtils.setField(llmExtractionService, "baseUrl", "http://localhost:" + mockPort + "/v1/messages");
    }

    @AfterEach
    void tearDown() {
        if (mockHttpServer != null) {
            mockHttpServer.stop(0);
        }
    }

    @Test
    @DisplayName("Scénario 1 : Mock HTTP 200 avec JSON malformé -> Vérifie 1 retry puis retombée sur le moteur de règles")
    void testMalformedJsonTriggersRetryThenFallback() {
        AtomicInteger requestCount = new AtomicInteger(0);

        mockHttpServer.createContext("/v1/messages", exchange -> {
            requestCount.incrementAndGet();
            // Réponse HTTP 200 avec un corps JSON invalide/tronqué (pas une erreur HTTP)
            String malformedJsonBody = "{ \"content\": [ { \"text\": \"{ budgetMax: 15000, invalid json structure ...\" } ] }";
            byte[] responseBytes = malformedJsonBody.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("3 pièces à Nema, budget 150000 FCFA, meublé");

        // Vérification 1 : Le service a bien tenté l'appel initial + 1 retry (total 2 requêtes)
        assertEquals(2, requestCount.get(), "Le client LLM aurait dû effectuer exactement 2 tentatives (essai initial + 1 retry)");

        // Vérification 2 : Après l'échec du retry, fallback sur le moteur de règles avec extraction cohérente
        assertNotNull(result);
        assertEquals("Nema", result.getQuartier());
        assertEquals(new BigDecimal("150000"), result.getBudgetMax());
        assertEquals(3, result.getNombrePieces());
        assertEquals(Boolean.TRUE, result.getMeuble());
    }

    @Test
    @DisplayName("Scénario 2 : Mock simulant une latence de 4s -> Vérifie fallback sous 2.5s grâce au timeout")
    void testLatencyTimeoutTriggersFallbackUnderLimit() {
        mockHttpServer.createContext("/v1/messages", exchange -> {
            try {
                // Simulation d'une latence serveur de 4.0 secondes (> 2.5s)
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String validResponse = "{ \"content\": [ { \"text\": \"{\\\"budgetMax\\\":95000}\" } ] }";
            byte[] responseBytes = validResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            } catch (IOException ignored) {}
        });

        long start = System.currentTimeMillis();
        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("Je cherche un studio à Boucotte pour 95000 FCFA");
        long duration = System.currentTimeMillis() - start;

        // Vérification 1 : Le temps total mesuré reste bien sous les 2.8s (la coupure du timeout 2.5s a interrompu les 4s de latence)
        assertTrue(duration < 2800, "Le temps de réponse total doit être coupé par le timeout à ~2.5s (obtenu: " + duration + "ms, latence mock: 4000ms)");

        // Vérification 2 : Le fallback moteur de règles a pris le relais et extrait correctement les critères
        assertNotNull(result);
        assertEquals(TypeBien.STUDIO, result.getType());
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("95000"), result.getBudgetMax());
    }

    @Test
    @DisplayName("Scénario 3 : Provider Mistral avec response_format json_object -> Extraction valide sans fallback")
    void testMistralProviderExtraction() {
        ReflectionTestUtils.setField(llmExtractionService, "provider", "mistral");

        mockHttpServer.createContext("/v1/chat/completions", exchange -> {
            String mistralResponse = """
                {
                  "id": "cmpl-123456",
                  "object": "chat.completion",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"budgetMax\\":95000,\\"type\\":\\"APPARTEMENT\\",\\"quartier\\":\\"Boucotte\\",\\"confiance\\":0.85}"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;
            byte[] responseBytes = mistralResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        ReflectionTestUtils.setField(llmExtractionService, "baseUrl", "http://localhost:" + mockPort + "/v1/chat/completions");

        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("Appartement à Boucotte pour 95000 FCFA");

        assertNotNull(result);
        assertEquals(TypeBien.APPARTEMENT, result.getType());
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("95000"), result.getBudgetMax());
        assertEquals(0.85, result.getConfiance());
    }
}

