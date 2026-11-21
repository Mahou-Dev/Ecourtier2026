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
 * Fournisseur LLM Anthropic Claude (/v1/messages), activable par configuration (AI_PROVIDER=claude).
 */
@Service
@Slf4j
public class ClaudeLLMProvider implements LLMProvider {

    private static final String DEFAULT_MODEL = "claude-3-5-haiku-latest";

    @Value("${ecourtier.ia.claude.api-key:${ANTHROPIC_API_KEY:}}")
    private String apiKey;

    @Value("${ecourtier.ia.claude.base-url:https://api.anthropic.com/v1}")
    private String baseUrl;

    @Value("${ecourtier.ia.claude.model:" + DEFAULT_MODEL + "}")
    private String model;

    @Value("${ecourtier.ia.claude.timeout-ms:4000}")
    private int timeoutMs;

    private final ObjectMapper objectMapper;
    private final GeminiLLMProvider jsonParserHelper;

    public ClaudeLLMProvider(ObjectMapper objectMapper, GeminiLLMProvider jsonParserHelper) {
        this.objectMapper = objectMapper;
        this.jsonParserHelper = jsonParserHelper;
    }

    @Override
    public String getProviderName() {
        return "claude";
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
            throw new IllegalStateException("La clé API Anthropic Claude (ANTHROPIC_API_KEY) n'est pas configurée.");
        }
        String prompt = """
                Tu es l'assistant immobilier d'E-Courtier Sénégal. Extrais les critères immobiliers du message suivant au format JSON strict :
                {"budgetMin":null,"budgetMax":null,"type":null,"modeTransaction":null,"quartier":null,"quartiers":null,"ville":null,"nombrePieces":null,"nombreChambres":null,"etage":null,"ascenseur":null,"meuble":null,"animauxAcceptes":null,"superficieMin":null,"superficieMax":null,"equipements":null,"proximites":null,"confiance":0.9,"questionDeRelance":null}
                Message : <user_query_untrusted>
                """ + userMessage + "\n</user_query_untrusted>";
        String raw = callClaudeMessages(prompt, 500);
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
            throw new IllegalStateException("La clé API Anthropic Claude (ANTHROPIC_API_KEY) n'est pas configurée.");
        }
        String prompt = "Tu es Samba, conseiller immobilier IA d'E-Courtier Sénégal. Réponds brièvement au message : " + userMessage;
        return callClaudeMessages(prompt, 350).trim();
    }

    private String callClaudeMessages(String userContent, int maxTokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : 4000;
        factory.setConnectTimeout(effectiveTimeout);
        factory.setReadTimeout(effectiveTimeout);
        RestTemplate restTemplate = new RestTemplate(factory);

        String cleanBase = baseUrl != null ? baseUrl.trim().replaceAll("/+$", "") : "https://api.anthropic.com/v1";
        String endpoint = cleanBase.endsWith("/messages") ? cleanBase : cleanBase + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey.trim());
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", getEffectiveModel());
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(Map.of("role", "user", "content", userContent)));

        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Réponse Claude invalide");
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("content").get(0).path("text").asText("");
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de lecture de la réponse Claude", e);
        }
    }
}
