package com.ecourtier.domain.ia.client;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fournisseur LLM OpenAI compatible (/v1/chat/completions), activable par configuration (AI_PROVIDER=openai).
 */
@Service
@Slf4j
public class OpenAILLMProvider implements LLMProvider {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    @Value("${ecourtier.ia.openai.api-key:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${ecourtier.ia.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ecourtier.ia.openai.model:" + DEFAULT_MODEL + "}")
    private String model;

    @Value("${ecourtier.ia.openai.timeout-ms:4000}")
    private int timeoutMs;

    private final ObjectMapper objectMapper;
    private final GeminiLLMProvider jsonParserHelper;

    public OpenAILLMProvider(ObjectMapper objectMapper, GeminiLLMProvider jsonParserHelper) {
        this.objectMapper = objectMapper;
        this.jsonParserHelper = jsonParserHelper;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getEffectiveModel() {
        return (model != null && !model.isBlank()) ? model.trim() : DEFAULT_MODEL;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public CriteresExtraitsDTO extractCriteria(String userMessage) {
        if (!isConfigured()) {
            throw new IllegalStateException("La clé API OpenAI (OPENAI_API_KEY) n'est pas configurée.");
        }
        String systemPrompt = """
                Tu es l'assistant immobilier d'E-Courtier Sénégal. Extrais les critères immobiliers du message client au format JSON strict :
                {"budgetMin":null,"budgetMax":null,"type":null,"modeTransaction":null,"quartier":null,"quartiers":null,"ville":null,"nombrePieces":null,"nombreChambres":null,"etage":null,"ascenseur":null,"meuble":null,"animauxAcceptes":null,"superficieMin":null,"superficieMax":null,"equipements":null,"proximites":null,"confiance":0.9,"questionDeRelance":null}
                """;
        String raw = callChatCompletions(systemPrompt, "<user_query_untrusted>\n" + userMessage + "\n</user_query_untrusted>", 0.1, 500);
        CriteresExtraitsDTO dto = jsonParserHelper.parseCriteriaJson(raw);
        dto.setFournisseurIa(getProviderName());
        dto.setModeSecours(false);
        return dto;
    }

    @Override
    public String generateConversationalResponse(
            String userMessage,
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> annoncesTrouvees,
            String questionDeRelance
    ) {
        if (!isConfigured()) {
            throw new IllegalStateException("La clé API OpenAI (OPENAI_API_KEY) n'est pas configurée.");
        }
        String systemPrompt = "Tu es Samba, conseiller immobilier IA d'E-Courtier Sénégal. Réponds en français en te basant uniquement sur les annonces réelles fournies.";
        String userPayload = "Message: " + userMessage + "\nAnnonces trouvées: " + (annoncesTrouvees != null ? annoncesTrouvees.size() : 0);
        return callChatCompletions(systemPrompt, userPayload, 0.2, 350).trim();
    }

    private String callChatCompletions(String systemPrompt, String userMessage, double temp, int maxTokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : 4000;
        factory.setConnectTimeout(effectiveTimeout);
        factory.setReadTimeout(effectiveTimeout);
        RestTemplate restTemplate = new RestTemplate(factory);

        String endpoint = (baseUrl != null ? baseUrl.trim().replaceAll("/+$", "") : "https://api.openai.com/v1") + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getEffectiveModel());
        body.put("temperature", temp);
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));

        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Réponse OpenAI invalide");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de lecture de la réponse OpenAI", e);
        }
    }
}
