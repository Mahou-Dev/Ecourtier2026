package com.ecourtier.domain.ia.client;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GeminiLLMProviderTest {

    private GeminiLLMProvider geminiProvider;
    private HttpServer mockServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        geminiProvider = new GeminiLLMProvider(new ObjectMapper());

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.start();

        ReflectionTestUtils.setField(geminiProvider, "apiKey", "dummy-gemini-secret-key-for-test");
        ReflectionTestUtils.setField(geminiProvider, "baseUrl", "http://localhost:" + mockPort + "/v1beta");
        ReflectionTestUtils.setField(geminiProvider, "model", "gemini-flash-lite-latest");
        ReflectionTestUtils.setField(geminiProvider, "timeoutMs", 2000);
        ReflectionTestUtils.setField(geminiProvider, "maxOutputTokens", 700);
        ReflectionTestUtils.setField(geminiProvider, "temperature", 0.1);
        geminiProvider.resetCooldown();
    }

    @AfterEach
    void tearDown() {
        if (geminiProvider != null) {
            geminiProvider.resetCooldown();
        }
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    @DisplayName("Gemini Test 1 : Configuration valide, nom du fournisseur et modèle")
    void testConfigurationValide() {
        assertTrue(geminiProvider.isConfigured());
        assertEquals("gemini", geminiProvider.getProviderName());
        assertEquals("gemini-flash-lite-latest", geminiProvider.getEffectiveModel());
    }

    @Test
    @DisplayName("Gemini Test 2 : Clé absente -> isConfigured() = false et exception à l'appel")
    void testCleAbsente() {
        ReflectionTestUtils.setField(geminiProvider, "apiKey", "");
        assertFalse(geminiProvider.isConfigured());
        assertThrows(IllegalStateException.class, () ->
                geminiProvider.extractCriteria("Je cherche un appartement à Dakar"));
    }

    @Test
    @DisplayName("Gemini Test 3 : Extraction HTTP 200 via en-tête x-goog-api-key (zéro clé dans l'URL)")
    void testExtractionHttp200AvecHeaderSecurise() {
        AtomicReference<String> receivedHeaderKey = new AtomicReference<>();
        AtomicReference<String> receivedQuery = new AtomicReference<>();

        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            receivedHeaderKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            receivedQuery.set(exchange.getRequestURI().getQuery());

            String jsonResp = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "{\\"type\\":\\"APPARTEMENT\\",\\"modeTransaction\\":\\"LOCATION\\",\\"ville\\":\\"Dakar\\",\\"quartier\\":\\"Mermoz\\",\\"quartiers\\":[\\"Mermoz\\",\\"Sacré-Cœur\\"],\\"budgetMax\\":250000,\\"nombrePieces\\":3,\\"nombreChambres\\":2,\\"superficieMin\\":80,\\"etage\\":2,\\"ascenseur\\":true,\\"meuble\\":true,\\"animauxAcceptes\\":true,\\"equipements\\":[\\"CLIMATISATION\\",\\"WIFI\\"],\\"proximites\\":[\\"TRANSPORT\\"],\\"confiance\\":0.96}"
                              }
                            ],
                            "role": "model"
                          }
                        }
                      ]
                    }
                    """;
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        CriteresExtraitsDTO res = geminiProvider.extractCriteria("Appartement 3 pièces à Mermoz ou Sacré-Cœur 250000 FCFA");

        assertEquals("dummy-gemini-secret-key-for-test", receivedHeaderKey.get());
        assertNull(receivedQuery.get(), "La clé API ne doit jamais transiter dans la query string de l'URL");

        assertNotNull(res);
        assertEquals("gemini", res.getFournisseurIa());
        assertEquals(Boolean.FALSE, res.getModeSecours());
        assertEquals(TypeBien.APPARTEMENT, res.getType());
        assertEquals(ModeTransaction.LOCATION, res.getModeTransaction());
        assertEquals("Dakar", res.getVille());
        assertEquals("Mermoz", res.getQuartier());
        assertEquals(List.of("Mermoz", "Sacré-Cœur"), res.getQuartiers());
        assertEquals(new BigDecimal("250000"), res.getBudgetMax());
        assertEquals(3, res.getNombrePieces());
        assertEquals(2, res.getNombreChambres());
        assertEquals(80.0, res.getSuperficieMin());
        assertEquals(2, res.getEtage());
        assertEquals(Boolean.TRUE, res.getAscenseur());
        assertEquals(Boolean.TRUE, res.getMeuble());
        assertEquals(Boolean.TRUE, res.getAnimauxAcceptes());
        assertTrue(res.getEquipements().contains(Equipement.CLIMATISATION));
        assertTrue(res.getProximites().contains(ProximiteTag.TRANSPORT));
    }

    @Test
    @DisplayName("Gemini Test 4 : Support des réponses multi-parts (thought + markdown JSON)")
    void testReponseMultiPartsThinkingEtMarkdown() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String jsonResp = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "thought": true,
                                "text": "Réflexion interne du modèle..."
                              },
                              {
                                "text": "```json\\n{\\"type\\":\\"VILLA\\",\\"modeTransaction\\":\\"VENTE\\",\\"ville\\":\\"Dakar\\",\\"quartier\\":\\"Almadies\\",\\"budgetMax\\":85000000,\\"confiance\\":0.92}\\n```"
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """;
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        CriteresExtraitsDTO res = geminiProvider.extractCriteria("Villa à vendre aux Almadies 85 millions");
        assertNotNull(res);
        assertEquals(TypeBien.VILLA, res.getType());
        assertEquals(ModeTransaction.VENTE, res.getModeTransaction());
        assertEquals("Almadies", res.getQuartier());
        assertEquals(new BigDecimal("85000000"), res.getBudgetMax());
    }

    @Test
    @DisplayName("Gemini Test 5 : Erreur HTTP 401/403 -> Exception levée sans exposition de la clé API")
    void testErreurHttp401SansFuiteDeCle() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String err = "{\"error\": {\"code\": 401, \"message\": \"API key not valid\", \"status\": \"UNAUTHENTICATED\"}}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        Exception ex = assertThrows(HttpClientErrorException.class, () ->
                geminiProvider.extractCriteria("Studio à Dakar"));
        assertFalse(ex.getMessage().contains("dummy-gemini-secret-key-for-test"), "La clé API Gemini ne doit jamais fuiter");
    }

    @Test
    @DisplayName("Gemini Test 6 : Erreur HTTP 429 Quota dépassé avec Retry-After -> Exception levée")
    void testErreurHttp429QuotaDepasse() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String err = "{\"error\": {\"code\": 429, \"message\": \"Resource has been exhausted\", \"status\": \"RESOURCE_EXHAUSTED\"}}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", "30");
            exchange.sendResponseHeaders(429, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThrows(HttpClientErrorException.class, () ->
                geminiProvider.extractCriteria("Villa à Saly"));
    }

    @Test
    @DisplayName("Gemini Test 7 : Erreur HTTP 500 Serveur -> HttpServerErrorException")
    void testErreurHttp500() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        assertThrows(HttpServerErrorException.class, () ->
                geminiProvider.extractCriteria("Appartement à Dakar"));
    }

    @Test
    @DisplayName("Gemini Test 8 : Timeout réseau -> Coupure rapide sous 1s")
    void testTimeoutReseau() {
        ReflectionTestUtils.setField(geminiProvider, "timeoutMs", 400);

        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {}
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        long start = System.currentTimeMillis();
        assertThrows(RestClientException.class, () ->
                geminiProvider.extractCriteria("Studio à Ouakam"));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 1200, "Le timeout Gemini doit couper l'appel sous 1.2s (obtenu: " + elapsed + "ms)");
    }

    @Test
    @DisplayName("Gemini Test 9 : Génération conversationnelle ancrée sur les annonces réelles")
    void testGenerationConversationnelle() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String jsonResp = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "Voici l'appartement vérifié à Mermoz pour 220 000 FCFA qui correspond parfaitement à votre budget de 250 000 FCFA."
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """;
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        AnnonceResponseDTO annonce = AnnonceResponseDTO.builder()
                .titre("Appartement F3 Mermoz")
                .prix(new BigDecimal("220000"))
                .quartier("Mermoz")
                .ville("Dakar")
                .statutVerification(StatutVerification.VERIFIEE)
                .build();

        String response = geminiProvider.generateConversationalResponse(
                "Je cherche un F3 à Mermoz",
                CriteresExtraitsDTO.builder().ville("Dakar").quartier("Mermoz").budgetMax(new BigDecimal("250000")).build(),
                List.of(annonce),
                null
        );

        assertNotNull(response);
        assertTrue(response.contains("Mermoz"));
        assertTrue(response.contains("220 000 FCFA"));
    }

    @Test
    @DisplayName("Gemini Test 10 : Erreurs HTTP 400, 403, 404 et 503 -> Exception propre sans fuite de la clé API")
    void testErreursHttpClientsEtServeurSansFuite() {
        for (int status : new int[]{400, 403, 404, 503}) {
            HttpServer tempServer = null;
            try {
                tempServer = HttpServer.create(new InetSocketAddress(0), 0);
                int port = tempServer.getAddress().getPort();
                tempServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
                    String err = "{\"error\":{\"code\":" + status + ",\"message\":\"Simulated HTTP " + status + " for key dummy-gemini-secret-key-for-test\"}}";
                    byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
                tempServer.start();
                ReflectionTestUtils.setField(geminiProvider, "baseUrl", "http://localhost:" + port + "/v1beta");
                geminiProvider.resetCooldown();

                Exception ex = assertThrows(RuntimeException.class, () ->
                        geminiProvider.extractCriteria("Appartement à Dakar"));
                assertFalse(ex.getMessage().contains("dummy-gemini-secret-key-for-test"),
                        "HTTP " + status + " ne doit jamais exposer la clé API");
            } catch (IOException e) {
                fail(e);
            } finally {
                if (tempServer != null) {
                    tempServer.stop(0);
                }
            }
        }
    }

    @Test
    @DisplayName("Gemini Test 11 : Réponse contenant uniquement des blocs thought=true -> jamais divulguée et exception levée")
    void testBlocsThinkingUniquementJamaisDivulgues() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String jsonResp = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "thought": true,
                                "text": "INTERNE_THOUGHT_CONFIDENTIEL {\"type\":\"STUDIO\"}"
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """;
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        IllegalStateException extractEx = assertThrows(IllegalStateException.class, () ->
                geminiProvider.extractCriteria("Studio à Dakar"));
        assertFalse(extractEx.getMessage().contains("INTERNE_THOUGHT_CONFIDENTIEL"));

        IllegalStateException convEx = assertThrows(IllegalStateException.class, () ->
                geminiProvider.generateConversationalResponse("Studio", CriteresExtraitsDTO.builder().build(), List.of(), null));
        assertFalse(convEx.getMessage().contains("INTERNE_THOUGHT_CONFIDENTIEL"));
    }

    @Test
    @DisplayName("Gemini Test 12 : Réponse conversationnelle multi-parts ignore les pensées internes et concatène les textes publics")
    void testGenerationConversationnelleMultiPartsIgnoreThought() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String jsonResp = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "thought": true,
                                "text": "PENSEE_SECRETE"
                              },
                              {
                                "text": "Bonjour ! Voici un bien vérifié à Mermoz."
                              },
                              {
                                "text": "Souhaitez-vous organiser une visite ?"
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """;
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        String response = geminiProvider.generateConversationalResponse(
                "Appartement Mermoz",
                CriteresExtraitsDTO.builder().quartier("Mermoz").build(),
                List.of(),
                null
        );
        assertFalse(response.contains("PENSEE_SECRETE"));
        assertTrue(response.contains("Bonjour ! Voici un bien vérifié à Mermoz."));
        assertTrue(response.contains("Souhaitez-vous organiser une visite ?"));
    }

    @Test
    @DisplayName("Gemini Test 13 : Corps vide ou candidats absents -> exception propre pour déclencher le fallback")
    void testReponseVideOuSansCandidats() {
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            String jsonResp = "{\"candidates\": []}";
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThrows(IllegalStateException.class, () ->
                geminiProvider.extractCriteria("Appartement à Dakar"));
    }

    @Test
    @DisplayName("Gemini Test 14 : HTTP 429 avec Retry-After active le cooldown non bloquant et évite un appel HTTP immédiat")
    void testCooldownNonBloquantApresHttp429() {
        AtomicInteger callCount = new AtomicInteger(0);
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            callCount.incrementAndGet();
            String err = "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\"}}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", "30");
            exchange.sendResponseHeaders(429, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThrows(HttpClientErrorException.class, () ->
                geminiProvider.extractCriteria("Villa à Saly"));
        assertTrue(geminiProvider.isInCooldown(), "Un HTTP 429 doit activer la fenêtre de cooldown");
        assertEquals(1, callCount.get());

        long start = System.currentTimeMillis();
        assertThrows(IllegalStateException.class, () ->
                geminiProvider.extractCriteria("Studio à Dakar"));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 200, "L'appel pendant cooldown doit échouer immédiatement sans Thread.sleep");
        assertEquals(1, callCount.get(), "Aucun appel HTTP sortant ne doit être émis tant que le cooldown est actif");
    }

    @Test
    @DisplayName("Gemini Test 15 : Normalisation des guillemets/espaces, repli IA_API_KEY Google (AIza*) et rejet clé Groq (gsk_*)")
    void testResolutionCleRobusteEtRejetCleGroq() {
        ReflectionTestUtils.setField(geminiProvider, "apiKey", "  \"AIzaDummyQuotedGeminiKey987654\"  ");
        ReflectionTestUtils.setField(geminiProvider, "legacyIaApiKey", "");
        assertTrue(geminiProvider.isConfigured());
        assertEquals("AIzaDummyQuotedGeminiKey987654", geminiProvider.resolveEffectiveApiKey());

        ReflectionTestUtils.setField(geminiProvider, "apiKey", "");
        ReflectionTestUtils.setField(geminiProvider, "legacyIaApiKey", "gsk_" + "legacyDummyKey");
        assertFalse(geminiProvider.isConfigured(), "Une clé Groq gsk_ dans IA_API_KEY ne doit jamais être utilisée pour Gemini");

        ReflectionTestUtils.setField(geminiProvider, "legacyIaApiKey", "AIzaLegacyGoogleKey123456");
        assertTrue(geminiProvider.isConfigured());
        assertEquals("AIzaLegacyGoogleKey123456", geminiProvider.resolveEffectiveApiKey());

        ReflectionTestUtils.setField(geminiProvider, "apiKey", "Bearer AIzaBearerPrefixedKey123");
        assertEquals("AIzaBearerPrefixedKey123", geminiProvider.resolveEffectiveApiKey());

        ReflectionTestUtils.setField(geminiProvider, "apiKey", "key=AIzaKeyEqualsPrefixedKey456");
        assertEquals("AIzaKeyEqualsPrefixedKey456", geminiProvider.resolveEffectiveApiKey());

        String diagnostic = geminiProvider.getDiagnosticEnvSummary();
        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("GEMINI_API_KEY="));
        assertTrue(diagnostic.contains("/etc/secrets="));
    }
}
