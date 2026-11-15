package com.ecourtier.domain.ia.client;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
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
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GroqClientServiceTest {

    private GroqClientService groqClientService;
    private HttpServer mockServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        groqClientService = new GroqClientService(objectMapper);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.start();

        ReflectionTestUtils.setField(groqClientService, "apiKey", "dummy-groq-key");
        ReflectionTestUtils.setField(groqClientService, "baseUrl", "http://localhost:" + mockPort + "/openai/v1");
        ReflectionTestUtils.setField(groqClientService, "model", "openai/gpt-oss-20b");
        ReflectionTestUtils.setField(groqClientService, "timeoutMs", 2000);
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    @DisplayName("Test 1 : Configuration valide et présence de clé")
    void testConfigurationValide() {
        assertTrue(groqClientService.isConfigured());
        assertEquals("openai/gpt-oss-20b", groqClientService.getEffectiveModel());
    }

    @Test
    @DisplayName("Test 2 : Clé absente -> isConfigured() = false et exception à l'extraction")
    void testCleAbsente() {
        ReflectionTestUtils.setField(groqClientService, "apiKey", "");
        assertFalse(groqClientService.isConfigured());

        assertThrows(IllegalStateException.class, () ->
                groqClientService.extractCriteria("Je cherche un appartement"));
    }

    @Test
    @DisplayName("Test 3 : Réponse HTTP 200 JSON valide -> Extraction critères conformes")
    void testExtractionReussieHttp200() throws Exception {
        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            String jsonResp = """
                    {
                      "id": "chatcmpl-test-123",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "{\\"type\\":\\"CHAMBRE\\",\\"modeTransaction\\":\\"LOCATION\\",\\"ville\\":\\"Ziguinchor\\",\\"quartier\\":\\"Boucotte\\",\\"budgetMax\\":75000,\\"confiance\\":0.95}"
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

        CriteresExtraitsDTO res = groqClientService.extractCriteria("Je cherche une chambre à Boucotte Ziguinchor max 75 000 FCFA");

        assertNotNull(res);
        assertEquals(TypeBien.CHAMBRE, res.getType());
        assertEquals(ModeTransaction.LOCATION, res.getModeTransaction());
        assertEquals("Ziguinchor", res.getVille());
        assertEquals("Boucotte", res.getQuartier());
        assertEquals(new BigDecimal("75000"), res.getBudgetMax());
        assertEquals(0.95, res.getConfiance());
    }

    @Test
    @DisplayName("Test 4 : Réponse HTTP 200 entourée de balises markdown ```json -> Nettoyage et parsing réussi")
    void testReponseMarkdownNettoyee() throws Exception {
        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            String jsonResp = """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "```json\\n{\\"type\\":\\"APPARTEMENT\\",\\"modeTransaction\\":\\"VENTE\\",\\"ville\\":\\"Dakar\\",\\"budgetMax\\":45000000,\\"confiance\\":0.9}\\n```"
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

        CriteresExtraitsDTO res = groqClientService.extractCriteria("Acheter appartement à Dakar 45 millions");

        assertNotNull(res);
        assertEquals(TypeBien.APPARTEMENT, res.getType());
        assertEquals(ModeTransaction.VENTE, res.getModeTransaction());
        assertEquals("Dakar", res.getVille());
        assertEquals(new BigDecimal("45000000"), res.getBudgetMax());
    }

    @Test
    @DisplayName("Test 5 : Erreur HTTP 401 Unauthorized -> Exception levée sans fuite de clé")
    void testErreurHttp401() {
        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            String err = "{\"error\": {\"message\": \"Invalid API key\", \"type\": \"invalid_request_error\"}}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        Exception ex = assertThrows(HttpClientErrorException.class, () ->
                groqClientService.extractCriteria("appartement à Dakar"));
        assertTrue(ex.getMessage().contains("401"));
        assertFalse(ex.getMessage().contains("dummy-groq-key"), "La clé API ne doit jamais fuiter");
    }

    @Test
    @DisplayName("Test 6 : Erreur HTTP 429 Rate Limit / Quota dépassé -> Exception levée")
    void testErreurHttp429Quota() {
        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            String err = "{\"error\": {\"message\": \"Rate limit exceeded\", \"type\": \"tokens\"}}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThrows(HttpClientErrorException.class, () ->
                groqClientService.extractCriteria("villa à Saly"));
    }

    @Test
    @DisplayName("Test 7 : Erreur HTTP 500 Serveur Groq -> Exception levée")
    void testErreurHttp500Serveur() {
        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });

        assertThrows(HttpServerErrorException.class, () ->
                groqClientService.extractCriteria("villa à Dakar"));
    }

    @Test
    @DisplayName("Test 8 : Latence / Timeout -> ResourceAccessException levée")
    void testTimeoutServeur() {
        ReflectionTestUtils.setField(groqClientService, "timeoutMs", 500);

        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(1500); // 1.5s > 500ms timeout
            } catch (InterruptedException ignored) {}
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThrows(org.springframework.web.client.RestClientException.class, () ->
                groqClientService.extractCriteria("studio à Dakar"));
    }

    @Test
    @DisplayName("Test 9 : Génération conversationnelle avec annonces réelles")
    void testGenerationConversationnelleAvecAnnoncesReelles() {
        mockServer.createContext("/openai/v1/chat/completions", exchange -> {
            String jsonResp = """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "J'ai trouvé un superbe appartement vérifié de 3 pièces à Fann Hock pour votre budget de 250 000 FCFA."
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

        AnnonceResponseDTO a = AnnonceResponseDTO.builder()
                .titre("Appartement 3 pièces Fann Hock")
                .prix(new BigDecimal("250000"))
                .quartier("Fann Hock")
                .ville("Dakar")
                .statutVerification(StatutVerification.VERIFIEE)
                .build();

        String rep = groqClientService.generateConversationalResponse(
                "Montre-moi les annonces",
                CriteresExtraitsDTO.builder().ville("Dakar").budgetMax(new BigDecimal("250000")).build(),
                List.of(a),
                null
        );

        assertNotNull(rep);
        assertTrue(rep.contains("Fann Hock"));
    }
}
