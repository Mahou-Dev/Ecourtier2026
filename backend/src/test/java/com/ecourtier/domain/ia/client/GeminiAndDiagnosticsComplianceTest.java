package com.ecourtier.domain.ia.client;

import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.ecourtier.domain.ia.service.LlmExtractionService;
import com.ecourtier.domain.ia.service.RuleBasedCriteriaExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Conformité Gemini & Diagnostics Fallback Local (RENDER-GEMINI-06)")
class GeminiAndDiagnosticsComplianceTest {

    private static final String FAKE_SECRET_KEY = "AIzaTestFakeSecretKey9876543210NeverLeak";

    @Mock
    private GroqClientService groqClientService;

    @Mock
    private OpenAILLMProvider openAILLMProvider;

    @Mock
    private ClaudeLLMProvider claudeLLMProvider;

    private RuleBasedCriteriaExtractor ruleBasedExtractor;
    private ObjectMapper objectMapper;
    private GeminiLLMProvider geminiProvider;
    private LlmExtractionService extractionService;
    private HttpServer mockServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        ruleBasedExtractor = new RuleBasedCriteriaExtractor();
        geminiProvider = new GeminiLLMProvider(objectMapper);

        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.start();

        ReflectionTestUtils.setField(geminiProvider, "apiKey", FAKE_SECRET_KEY);
        ReflectionTestUtils.setField(geminiProvider, "baseUrl", "http://127.0.0.1:" + mockPort + "/v1beta");
        ReflectionTestUtils.setField(geminiProvider, "model", "gemini-flash-lite-latest");
        ReflectionTestUtils.setField(geminiProvider, "timeoutMs", 800);
        ReflectionTestUtils.setField(geminiProvider, "maxOutputTokens", 700);
        ReflectionTestUtils.setField(geminiProvider, "temperature", 0.1);

        extractionService = new LlmExtractionService(
                ruleBasedExtractor,
                objectMapper,
                groqClientService,
                geminiProvider,
                openAILLMProvider,
                claudeLLMProvider
        );
        ReflectionTestUtils.setField(extractionService, "iaEnabled", true);
        ReflectionTestUtils.setField(extractionService, "provider", "gemini");
        ReflectionTestUtils.setField(extractionService, "fallbackProvider", "local");
    }

    @AfterEach
    void tearDown() {
        geminiProvider.resetCooldown();
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    @DisplayName("Nominal Gemini HTTP 200 (sans appel Groq/OpenAI/Claude) + non-écrasement du comparatif par règles locales")
    void testNominalGeminiEtComparatifPreserve() {
        AtomicInteger callCount = new AtomicInteger(0);
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            callCount.incrementAndGet();
            assertEquals(FAKE_SECRET_KEY, exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            assertNull(exchange.getRequestURI().getQuery());

            String body = """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [{
                            "text": "{\\"type\\":\\"STUDIO\\",\\"modeTransaction\\":\\"LOCATION\\",\\"ville\\":\\"Dakar\\",\\"budgetMax\\":200000,\\"preferencesSouples\\":[\\"logement meublé de préférence\\"],\\"confiance\\":0.95}"
                          }]
                        }
                      }]
                    }
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        CriteresExtraitsDTO res = extractionService.extractCriteria("Je préférerais un studio plutôt qu'une chambre à Dakar 200 000 FCFA");
        assertEquals(1, callCount.get());
        assertEquals("gemini", res.getFournisseurIa());
        assertEquals(Boolean.FALSE, res.getModeSecours());
        assertNull(res.getMotifSecours());
        assertEquals(TypeBien.STUDIO, res.getType());
        assertEquals("Dakar", res.getVille());

        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("Diagnostic CLE_ABSENTE lorsque GEMINI_API_KEY est absente")
    void testDiagnosticCleAbsente() {
        ReflectionTestUtils.setField(geminiProvider, "apiKey", "");

        CriteresExtraitsDTO res = extractionService.extractCriteria("Studio à Dakar pour 150 000 FCFA");
        assertEquals("fallback-local", res.getFournisseurIa());
        assertEquals(Boolean.TRUE, res.getModeSecours());
        assertEquals("CLE_ABSENTE", res.getMotifSecours());

        LlmExtractionService.GenerationOutcome gen = extractionService.generateConversationalResponseWithDiagnostic(
                "Bonjour", res, List.of(), null);
        assertNull(gen.responseText());
        assertEquals("CLE_ABSENTE", gen.motifSecours());
        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("Diagnostic IA_DESACTIVEE lorsque ecourtier.ia.enabled=false")
    void testDiagnosticIaDesactivee() {
        ReflectionTestUtils.setField(extractionService, "iaEnabled", false);

        CriteresExtraitsDTO res = extractionService.extractCriteria("Appartement à Dakar");
        assertEquals("fallback-local", res.getFournisseurIa());
        assertEquals(Boolean.TRUE, res.getModeSecours());
        assertEquals("IA_DESACTIVEE", res.getMotifSecours());
        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("Diagnostic FOURNISSEUR_NON_AUTORISE et rejet runtime pour groq/openai/claude")
    void testFournisseursNonAutorisesRejetes() {
        for (String forbidden : List.of("groq", "openai", "claude")) {
            ReflectionTestUtils.setField(extractionService, "provider", forbidden);
            CriteresExtraitsDTO res = extractionService.extractCriteria("Villa à Dakar");
            assertEquals("fallback-local", res.getFournisseurIa());
            assertEquals(Boolean.TRUE, res.getModeSecours());
            assertEquals("FOURNISSEUR_NON_AUTORISE", res.getMotifSecours());
        }
        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("Diagnostic QUOTA sur HTTP 429 avec Retry-After et activation cooldown")
    void testDiagnosticQuotaHttp429EtCooldown() {
        AtomicInteger calls = new AtomicInteger(0);
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "30");
            byte[] err = "{\"error\":{\"code\":429,\"message\":\"RESOURCE_EXHAUSTED\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        });

        CriteresExtraitsDTO res1 = extractionService.extractCriteria("Studio à Mermoz");
        assertEquals(Boolean.TRUE, res1.getModeSecours());
        assertEquals("QUOTA", res1.getMotifSecours());
        assertFalse(res1.toString().contains(FAKE_SECRET_KEY));

        int callsAfterFirst = calls.get();
        CriteresExtraitsDTO res2 = extractionService.extractCriteria("Villa à Almadies");
        assertEquals("QUOTA", res2.getMotifSecours());
        assertEquals(callsAfterFirst, calls.get(), "Pendant le cooldown Retry-After, aucun nouvel appel HTTP ne doit partir");
        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("Diagnostic ERREUR_HTTP sur codes HTTP 400, 401, 403, 404, 500, 503 sans fuite de clé")
    void testDiagnosticCodesHttpDivers() {
        int[] codes = {400, 401, 403, 404, 500, 503};
        AtomicInteger idx = new AtomicInteger(0);
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            int code = codes[Math.min(idx.get(), codes.length - 1)];
            byte[] err = ("{\"error\":{\"code\":" + code + ",\"message\":\"err key " + FAKE_SECRET_KEY + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        });

        for (int i = 0; i < codes.length; i++) {
            idx.set(i);
            geminiProvider.resetCooldown();
            CriteresExtraitsDTO res = extractionService.extractCriteria("Chambre à Dakar");
            assertEquals("fallback-local", res.getFournisseurIa());
            assertEquals(Boolean.TRUE, res.getModeSecours());
            assertEquals("ERREUR_HTTP", res.getMotifSecours());
            assertFalse(res.toString().contains(FAKE_SECRET_KEY));
        }
        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("Diagnostic TIMEOUT et REPONSE_INVALIDE sans blocage ni fuite")
    void testDiagnosticTimeoutEtReponseInvalide() {
        ReflectionTestUtils.setField(geminiProvider, "timeoutMs", 250);
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });

        CriteresExtraitsDTO resTimeout = extractionService.extractCriteria("Studio à Ouakam");
        assertEquals("fallback-local", resTimeout.getFournisseurIa());
        assertEquals(Boolean.TRUE, resTimeout.getModeSecours());
        assertEquals("TIMEOUT", resTimeout.getMotifSecours());

        // Second scenario with fast HTTP 200 invalid JSON payload
        mockServer.removeContext("/v1beta/models/gemini-flash-lite-latest:generateContent");
        ReflectionTestUtils.setField(geminiProvider, "timeoutMs", 1000);
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            byte[] invalid = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not-json\"}]}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, invalid.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(invalid);
            }
        });

        CriteresExtraitsDTO resInvalid = extractionService.extractCriteria("Appartement à Fann");
        assertEquals("fallback-local", resInvalid.getFournisseurIa());
        assertEquals(Boolean.TRUE, resInvalid.getModeSecours());
        assertEquals("REPONSE_INVALIDE", resInvalid.getMotifSecours());
        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }

    @Test
    @DisplayName("REV-01 : RuleBasedCriteriaExtractor ne fige pas CLE_ABSENTE sur FAQ juridique ; LlmExtractionService reflète le motif réel")
    void testRev01FaqJuridiqueDiagnosticSelonConfiguration() {
        String faqQuestion = "Comment être sûr que les annonces sont fiables et authentiques ?";

        // 1. Extracteur local isolé : ne doit pas coder en dur CLE_ABSENTE
        CriteresExtraitsDTO isolated = ruleBasedExtractor.extractCriteria(faqQuestion);
        assertEquals("FAQ_VERIFICATION_JURIDIQUE", isolated.getIntentionSpeciale());
        assertNull(isolated.getMotifSecours(), "RuleBasedCriteriaExtractor ne doit pas hardcoder CLE_ABSENTE");

        // 2. Gemini désactivé -> IA_DESACTIVEE
        ReflectionTestUtils.setField(extractionService, "iaEnabled", false);
        CriteresExtraitsDTO disabledRes = extractionService.extractCriteria(faqQuestion);
        assertEquals("fallback-local", disabledRes.getFournisseurIa());
        assertEquals(Boolean.TRUE, disabledRes.getModeSecours());
        assertEquals("IA_DESACTIVEE", disabledRes.getMotifSecours());

        // 3. Fournisseur non autorisé -> FOURNISSEUR_NON_AUTORISE
        ReflectionTestUtils.setField(extractionService, "iaEnabled", true);
        ReflectionTestUtils.setField(extractionService, "provider", "groq");
        CriteresExtraitsDTO forbiddenRes = extractionService.extractCriteria(faqQuestion);
        assertEquals("fallback-local", forbiddenRes.getFournisseurIa());
        assertEquals(Boolean.TRUE, forbiddenRes.getModeSecours());
        assertEquals("FOURNISSEUR_NON_AUTORISE", forbiddenRes.getMotifSecours());

        // 4. Clé absente -> CLE_ABSENTE
        ReflectionTestUtils.setField(extractionService, "provider", "gemini");
        ReflectionTestUtils.setField(geminiProvider, "apiKey", "");
        CriteresExtraitsDTO noKeyRes = extractionService.extractCriteria(faqQuestion);
        assertEquals("fallback-local", noKeyRes.getFournisseurIa());
        assertEquals(Boolean.TRUE, noKeyRes.getModeSecours());
        assertEquals("CLE_ABSENTE", noKeyRes.getMotifSecours());

        // 5. Gemini actif et configuré -> éligible Gemini (modeSecours=false, motifSecours=null)
        ReflectionTestUtils.setField(geminiProvider, "apiKey", FAKE_SECRET_KEY);
        CriteresExtraitsDTO activeRes = extractionService.extractCriteria(faqQuestion);
        assertEquals("gemini", activeRes.getFournisseurIa());
        assertEquals(Boolean.FALSE, activeRes.getModeSecours());
        assertNull(activeRes.getMotifSecours());

        verifyNoInteractions(groqClientService, openAILLMProvider, claudeLLMProvider);
    }
}
