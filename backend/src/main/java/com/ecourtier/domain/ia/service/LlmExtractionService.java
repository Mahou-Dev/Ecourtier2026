package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class LlmExtractionService {

    private final RuleBasedCriteriaExtractor ruleBasedExtractor;
    private final ObjectMapper objectMapper;

    @Value("${ecourtier.ia.api-key:}")
    private String apiKey;

    @Value("${ecourtier.ia.provider:claude}")
    private String provider;

    @Value("${ecourtier.ia.base-url:}")
    private String baseUrl;

    private static final int TIMEOUT_MS = 2500; // 2.5s timeout requirement

    public CriteresExtraitsDTO extractCriteria(String userMessage) {
        // Step 1: Sanitization & injection check
        ruleBasedExtractor.sanitizeAndValidate(userMessage);

        // Step 2: Check API key availability
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.info("[MODE DÉMO IA] Clé API IA_API_KEY non configurée. Extraction par le moteur de règles déterministe.");
            return ruleBasedExtractor.extractCriteria(userMessage);
        }

        // Step 3: LLM API Call with Retry on malformed JSON and immediate fallback on timeout
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("[MODE LLM IA] Appel à l'API LLM (provider: {}, essai {}/{})", provider, attempt, maxAttempts);
                return callLlmApiWithTimeout(userMessage);
            } catch (Exception e) {
                log.warn("[MODE LLM IA] Échec de l'essai {}/{}: {}", attempt, maxAttempts, e.getMessage());

                // En cas de timeout ou erreur HTTP client (401/403/404), basculer immédiatement sur le moteur de règles
                boolean isNonRetryableError = e instanceof org.springframework.web.client.HttpClientErrorException;
                boolean isTimeout = e instanceof org.springframework.web.client.ResourceAccessException
                        || (e.getCause() != null && e.getCause() instanceof java.net.SocketTimeoutException);

                if (isTimeout || isNonRetryableError || attempt == maxAttempts) {
                    log.warn("[MODE FALLBACK IA] Retombée sur le moteur de règles (timeout = {}, nonRetryable = {}, max essais = {}).", isTimeout, isNonRetryableError, attempt);
                    return ruleBasedExtractor.extractCriteria(userMessage);
                }
            }
        }

        return ruleBasedExtractor.extractCriteria(userMessage);
    }

    private CriteresExtraitsDTO callLlmApiWithTimeout(String userMessage) throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .build();

        String systemPrompt = """
                Tu es l'assistant de recherche d'annonces immobilières d'E-Courtier Sénégal.
                Analyse le message de l'utilisateur et extrait les critères de recherche sous forme d'un objet JSON strict.
                RÈGLES STRICTES :
                - Ne remplis un champ QUE si l'utilisateur l'a explicitement mentionné dans son message.
                - Laisse null tout critère non précisé. Ne devine JAMAIS une valeur par défaut (ne mets JAMAIS 0, 0.0 ou false pour un champ non mentionné).
                - Le champ "confiance" doit refléter uniquement les champs réellement extraits avec certitude (ex: 0.8 si plusieurs critères précis sont fournis, 0.4 si vagues).
                Réponds UNIQUEMENT par le JSON pur sans aucun texte additionnel ni balises markdown.
                """;

        String defaultUrl = "gemini".equalsIgnoreCase(provider) ?
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent" :
                ("claude".equalsIgnoreCase(provider) ? "https://api.anthropic.com/v1/messages" : "https://api.openai.com/v1/chat/completions");

        String targetUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl : defaultUrl;

        if ("gemini".equalsIgnoreCase(provider) && (baseUrl == null || baseUrl.trim().isEmpty())) {
            Map<String, Object> schema = Map.of(
                    "type", "OBJECT",
                    "properties", Map.ofEntries(
                            Map.entry("budgetMin", Map.of("type", "NUMBER", "nullable", true)),
                            Map.entry("budgetMax", Map.of("type", "NUMBER", "nullable", true)),
                            Map.entry("type", Map.of("type", "STRING", "enum", List.of("STUDIO", "APPARTEMENT", "VILLA"), "nullable", true)),
                            Map.entry("modeTransaction", Map.of("type", "STRING", "enum", List.of("LOCATION", "VENTE"), "nullable", true)),
                            Map.entry("quartier", Map.of("type", "STRING", "nullable", true)),
                            Map.entry("ville", Map.of("type", "STRING", "nullable", true)),
                            Map.entry("nombrePieces", Map.of("type", "INTEGER", "nullable", true)),
                            Map.entry("meuble", Map.of("type", "BOOLEAN", "nullable", true)),
                            Map.entry("animauxAcceptes", Map.of("type", "BOOLEAN", "nullable", true)),
                            Map.entry("superficieMin", Map.of("type", "NUMBER", "nullable", true)),
                            Map.entry("confiance", Map.of("type", "NUMBER")),
                            Map.entry("questionDeRelance", Map.of("type", "STRING", "nullable", true))
                    ),
                    "required", List.of("confiance")
            );

            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", userMessage)))
                    ),
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", systemPrompt))
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "responseSchema", schema
                    )
            );

            String rawResponse = client.post()
                    .uri(targetUrl)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseLlmJsonResponse(rawResponse);
        } else if ("claude".equalsIgnoreCase(provider) || (baseUrl != null && !baseUrl.trim().isEmpty())) {
            Map<String, Object> body = Map.of(
                    "model", "claude-sonnet-4-6",
                    "max_tokens", 500,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", userMessage))
            );

            String rawResponse = client.post()
                    .uri(targetUrl)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseLlmJsonResponse(rawResponse);
        } else {
            // Fallback OpenAI Chat Completions API
            Map<String, Object> body = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            String rawResponse = client.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseLlmJsonResponse(rawResponse);
        }
    }

    private CriteresExtraitsDTO parseLlmJsonResponse(String rawResponse) {
        try {
            var node = objectMapper.readTree(rawResponse);
            String jsonText = "";
            if (node.has("candidates") && node.get("candidates").isArray() && !node.get("candidates").isEmpty()) {
                jsonText = node.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
            } else if (node.has("content") && node.get("content").isArray()) {
                jsonText = node.get("content").get(0).get("text").asText();
            } else if (node.has("choices") && node.get("choices").isArray()) {
                jsonText = node.get("choices").get(0).get("message").get("content").asText();
            } else {
                jsonText = rawResponse;
            }

            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceAll("^```[a-z]*\\n", "").replaceAll("\\n```$", "").trim();
            }

            CriteresExtraitsDTO result = objectMapper.readValue(jsonText, CriteresExtraitsDTO.class);
            return sanitizeAndCalibrate(result);
        } catch (Exception e) {
            log.error("Erreur de parsing de la réponse JSON du LLM", e);
            throw new RuntimeException("Réponse JSON LLM invalide", e);
        }
    }

    private CriteresExtraitsDTO sanitizeAndCalibrate(CriteresExtraitsDTO criteres) {
        if (criteres == null) return null;

        if (criteres.getBudgetMin() != null && criteres.getBudgetMin().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            criteres.setBudgetMin(null);
        }
        if (criteres.getBudgetMax() != null && criteres.getBudgetMax().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            criteres.setBudgetMax(null);
        }
        if (criteres.getSuperficieMin() != null && criteres.getSuperficieMin() <= 0) {
            criteres.setSuperficieMin(null);
        }
        if (criteres.getNombrePieces() != null && criteres.getNombrePieces() <= 0) {
            criteres.setNombrePieces(null);
        }

        // Recalibrer la confiance basée uniquement sur les critères réellement extraits
        int count = 0;
        if (criteres.getBudgetMin() != null || criteres.getBudgetMax() != null) count++;
        if (criteres.getType() != null) count++;
        if (criteres.getModeTransaction() != null) count++;
        if (criteres.getQuartier() != null || criteres.getVille() != null) count++;
        if (criteres.getNombrePieces() != null) count++;
        if (criteres.getMeuble() != null) count++;
        if (criteres.getAnimauxAcceptes() != null) count++;
        if (criteres.getSuperficieMin() != null) count++;

        if (count == 0) {
            criteres.setConfiance(0.2);
        } else {
            double calibrated = Math.min(1.0, 0.4 + (count * 0.15));
            criteres.setConfiance(Math.round(calibrated * 100.0) / 100.0);
        }

        if (criteres.getBudgetMax() == null && criteres.getBudgetMin() == null && (criteres.getQuestionDeRelance() == null || criteres.getQuestionDeRelance().trim().isEmpty())) {
            criteres.setQuestionDeRelance("Quel est votre budget approximatif et le type de bien souhaité (ex: appartement, villa, studio) ?");
        }

        return criteres;
    }
}
