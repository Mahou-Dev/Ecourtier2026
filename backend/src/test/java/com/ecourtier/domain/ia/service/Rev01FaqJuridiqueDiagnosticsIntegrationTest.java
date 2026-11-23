package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.client.GeminiLLMProvider;
import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.ecourtier.domain.ia.dto.MessageIaResponseDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.testsupport.TestDatabaseCleaner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("REV-01 : Diagnostics de secours et préservation contexte sur FAQ juridique")
class Rev01FaqJuridiqueDiagnosticsIntegrationTest {

    private static final String FAKE_KEY = "AIzaRev01FakeGeminiKeyNeverExpose9999";
    private static final String FAQ_PROMPT = "Comment être sûr que les annonces sont fiables et que le titre foncier est authentique ?";
    private static final String SEED_CRITERIA_PROMPT = "Je cherche un appartement à louer à Mermoz budget max 250000 FCFA";

    @Autowired
    private IAService iaService;

    @Autowired
    private LlmExtractionService llmExtractionService;

    @Autowired
    private GeminiLLMProvider geminiLLMProvider;

    @Autowired
    private IaRateLimiterService iaRateLimiterService;

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    private HttpServer mockServer;
    private Object origIaEnabled;
    private Object origProvider;
    private Object origApiKey;
    private Object origBaseUrl;
    private Object origTimeoutMs;

    @BeforeEach
    void setUp() throws IOException {
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();

        origIaEnabled = ReflectionTestUtils.getField(llmExtractionService, "iaEnabled");
        origProvider = ReflectionTestUtils.getField(llmExtractionService, "provider");
        origApiKey = ReflectionTestUtils.getField(geminiLLMProvider, "apiKey");
        origBaseUrl = ReflectionTestUtils.getField(geminiLLMProvider, "baseUrl");
        origTimeoutMs = ReflectionTestUtils.getField(geminiLLMProvider, "timeoutMs");

        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.start();
        int port = mockServer.getAddress().getPort();

        ReflectionTestUtils.setField(llmExtractionService, "iaEnabled", true);
        ReflectionTestUtils.setField(llmExtractionService, "provider", "gemini");
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", "");
        ReflectionTestUtils.setField(geminiLLMProvider, "baseUrl", "http://127.0.0.1:" + port + "/v1beta");
        ReflectionTestUtils.setField(geminiLLMProvider, "timeoutMs", 800);
        geminiLLMProvider.resetCooldown();
    }

    @AfterEach
    void tearDown() {
        geminiLLMProvider.resetCooldown();
        if (mockServer != null) {
            mockServer.stop(0);
        }
        ReflectionTestUtils.setField(llmExtractionService, "iaEnabled", origIaEnabled);
        ReflectionTestUtils.setField(llmExtractionService, "provider", origProvider);
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", origApiKey);
        ReflectionTestUtils.setField(geminiLLMProvider, "baseUrl", origBaseUrl);
        ReflectionTestUtils.setField(geminiLLMProvider, "timeoutMs", origTimeoutMs);
        iaRateLimiterService.clear();
        testDatabaseCleaner.cleanAll();
    }

    private ConversationCreateResponseDTO startConvWithPriorCriteria() {
        ConversationCreateResponseDTO conv = iaService.creerConversation(null);
        // Alimenter le contexte précédent via fallback local déterministe (clé vide initialement)
        Object savedKey = ReflectionTestUtils.getField(geminiLLMProvider, "apiKey");
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", "");
        iaService.envoyerMessage(conv.getConversationId(), new MessageUserRequestDTO(SEED_CRITERIA_PROMPT), null, conv.getSessionToken());
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", savedKey);
        return conv;
    }

    private void assertPriorCriteriaPreserved(CriteresExtraitsDTO c) {
        assertNotNull(c);
        assertEquals(TypeBien.APPARTEMENT, c.getType(), "Le type précédent doit être préservé sur FAQ juridique");
        assertEquals("Mermoz", c.getQuartier(), "Le quartier précédent doit être préservé sur FAQ juridique");
        assertEquals(0, new BigDecimal("250000").compareTo(c.getBudgetMax()), "Le budget précédent doit être préservé");
    }

    @Test
    @DisplayName("1. FAQ juridique avec Gemini désactivé (IA_ENABLED=false) -> motifSecours = IA_DESACTIVEE")
    void testFaqAvecGeminiDesactive() {
        ConversationCreateResponseDTO conv = startConvWithPriorCriteria();
        ReflectionTestUtils.setField(llmExtractionService, "iaEnabled", false);
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", FAKE_KEY);

        MessageIaResponseDTO resp = iaService.envoyerMessage(
                conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());

        assertEquals("fallback-local", resp.getCriteresExtraits().getFournisseurIa());
        assertEquals(Boolean.TRUE, resp.getCriteresExtraits().getModeSecours());
        assertEquals("IA_DESACTIVEE", resp.getCriteresExtraits().getMotifSecours());
        assertPriorCriteriaPreserved(resp.getCriteresExtraits());
        assertFalse(resp.getReponse().contains(FAKE_KEY));
    }

    @Test
    @DisplayName("2. FAQ juridique avec fournisseur non autorisé -> motifSecours = FOURNISSEUR_NON_AUTORISE")
    void testFaqAvecFournisseurNonAutorise() {
        ConversationCreateResponseDTO conv = startConvWithPriorCriteria();
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", FAKE_KEY);
        for (String provider : List.of("groq", "openai", "claude")) {
            ReflectionTestUtils.setField(llmExtractionService, "provider", provider);

            MessageIaResponseDTO resp = iaService.envoyerMessage(
                    conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());

            assertEquals("fallback-local", resp.getCriteresExtraits().getFournisseurIa());
            assertEquals(Boolean.TRUE, resp.getCriteresExtraits().getModeSecours());
            assertEquals("FOURNISSEUR_NON_AUTORISE", resp.getCriteresExtraits().getMotifSecours());
            assertPriorCriteriaPreserved(resp.getCriteresExtraits());
        }
    }

    @Test
    @DisplayName("3. FAQ juridique sans clé Gemini -> motifSecours = CLE_ABSENTE")
    void testFaqSansCleGemini() {
        ConversationCreateResponseDTO conv = startConvWithPriorCriteria();
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", "");

        MessageIaResponseDTO resp = iaService.envoyerMessage(
                conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());

        assertEquals("fallback-local", resp.getCriteresExtraits().getFournisseurIa());
        assertEquals(Boolean.TRUE, resp.getCriteresExtraits().getModeSecours());
        assertEquals("CLE_ABSENTE", resp.getCriteresExtraits().getMotifSecours());
        assertPriorCriteriaPreserved(resp.getCriteresExtraits());
    }

    @Test
    @DisplayName("4. FAQ juridique après timeout Gemini -> motifSecours = TIMEOUT")
    void testFaqApresTimeoutGemini() {
        ConversationCreateResponseDTO conv = startConvWithPriorCriteria();
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", FAKE_KEY);
        ReflectionTestUtils.setField(geminiLLMProvider, "timeoutMs", 250);

        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MessageIaResponseDTO resp = iaService.envoyerMessage(
                conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());

        assertEquals("fallback-local", resp.getCriteresExtraits().getFournisseurIa());
        assertEquals(Boolean.TRUE, resp.getCriteresExtraits().getModeSecours());
        assertEquals("TIMEOUT", resp.getCriteresExtraits().getMotifSecours());
        assertPriorCriteriaPreserved(resp.getCriteresExtraits());
        assertFalse(resp.getReponse().contains(FAKE_KEY));
    }

    @Test
    @DisplayName("5. FAQ juridique après erreur HTTP Gemini (500 et 200 vide) -> ERREUR_HTTP et REPONSE_INVALIDE")
    void testFaqApresErreurHttpEtReponseInvalide() {
        ConversationCreateResponseDTO conv = startConvWithPriorCriteria();
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", FAKE_KEY);

        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            byte[] bytes = ("{\"error\":{\"code\":500,\"message\":\"internal error " + FAKE_KEY + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MessageIaResponseDTO resp500 = iaService.envoyerMessage(
                conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());
        assertEquals("fallback-local", resp500.getCriteresExtraits().getFournisseurIa());
        assertEquals(Boolean.TRUE, resp500.getCriteresExtraits().getModeSecours());
        assertEquals("ERREUR_HTTP", resp500.getCriteresExtraits().getMotifSecours());
        assertPriorCriteriaPreserved(resp500.getCriteresExtraits());
        assertFalse(resp500.getReponse().contains(FAKE_KEY));

        // Cas réponse Gemini vide / sans texte public -> REPONSE_INVALIDE
        mockServer.removeContext("/v1beta/models/gemini-flash-lite-latest:generateContent");
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            byte[] bytes = "{\"candidates\":[{\"content\":{\"parts\":[]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MessageIaResponseDTO respInvalid = iaService.envoyerMessage(
                conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());
        assertEquals("fallback-local", respInvalid.getCriteresExtraits().getFournisseurIa());
        assertEquals(Boolean.TRUE, respInvalid.getCriteresExtraits().getModeSecours());
        assertEquals("REPONSE_INVALIDE", respInvalid.getCriteresExtraits().getMotifSecours());
        assertPriorCriteriaPreserved(respInvalid.getCriteresExtraits());
    }

    @Test
    @DisplayName("6. FAQ juridique avec Gemini nominal simulé (HTTP 200) -> fournisseurIa = gemini, modeSecours = false")
    void testFaqAvecGeminiNominalSimule() {
        ConversationCreateResponseDTO conv = startConvWithPriorCriteria();
        ReflectionTestUtils.setField(geminiLLMProvider, "apiKey", FAKE_KEY);

        String expectedText = "Le badge Vérifié contrôle les justificatifs fournis sans constituer une garantie notariale : faites vérifier le titre foncier auprès d'un notaire avant tout versement.";
        mockServer.createContext("/v1beta/models/gemini-flash-lite-latest:generateContent", exchange -> {
            assertEquals(FAKE_KEY, exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            String json = """
                    {
                      "candidates": [{
                        "content": {
                          "parts": [{
                            "text": "%s"
                          }]
                        }
                      }]
                    }
                    """.formatted(expectedText.replace("\"", "\\\""));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        MessageIaResponseDTO resp = iaService.envoyerMessage(
                conv.getConversationId(), new MessageUserRequestDTO(FAQ_PROMPT), null, conv.getSessionToken());

        assertEquals("gemini", resp.getCriteresExtraits().getFournisseurIa());
        assertEquals(Boolean.FALSE, resp.getCriteresExtraits().getModeSecours());
        assertNull(resp.getCriteresExtraits().getMotifSecours());
        assertEquals(expectedText, resp.getReponse());
        assertPriorCriteriaPreserved(resp.getCriteresExtraits());
        assertTrue(resp.getAnnoncesSuggeres().isEmpty());
    }
}
