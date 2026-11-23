package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.client.ClaudeLLMProvider;
import com.ecourtier.domain.ia.client.GeminiLLMProvider;
import com.ecourtier.domain.ia.client.GroqClientService;
import com.ecourtier.domain.ia.client.OpenAILLMProvider;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrateur IA strictement mono-fournisseur externe (RENDER-GEMINI-01) :
 * Google Gemini (principal et unique LLM externe autorisé) -> Fallback déterministe local ({@link RuleBasedCriteriaExtractor}).
 * Groq, OpenAI et Claude ne sont jamais appelés ni utilisés comme fournisseurs alternatifs.
 */
@Service
@Slf4j
public class LlmExtractionService {

    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_LOCAL = "local";

    @Value("${ecourtier.ia.enabled:${IA_ENABLED:true}}")
    private boolean iaEnabled = true;

    @Value("${ecourtier.ia.api-key:}")
    private String apiKey;

    @Value("${ecourtier.ia.provider:${AI_PROVIDER:${IA_PROVIDER:gemini}}}")
    private String provider = PROVIDER_GEMINI;

    @Value("${ecourtier.ia.fallback-provider:${AI_FALLBACK_PROVIDER:${IA_FALLBACK_PROVIDER:local}}}")
    private String fallbackProvider = PROVIDER_LOCAL;

    @Value("${ecourtier.ia.base-url:}")
    private String baseUrl;

    @Value("${ecourtier.ia.timeout-ms:2500}")
    private int llmTimeoutMs;

    private final RuleBasedCriteriaExtractor ruleBasedExtractor;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unused")
    private final GroqClientService groqClientService;
    private final GeminiLLMProvider geminiLLMProvider;
    @SuppressWarnings("unused")
    private final OpenAILLMProvider openAILLMProvider;
    @SuppressWarnings("unused")
    private final ClaudeLLMProvider claudeLLMProvider;

    @Autowired
    public LlmExtractionService(
            RuleBasedCriteriaExtractor ruleBasedExtractor,
            ObjectMapper objectMapper,
            GroqClientService groqClientService,
            GeminiLLMProvider geminiLLMProvider,
            OpenAILLMProvider openAILLMProvider,
            ClaudeLLMProvider claudeLLMProvider
    ) {
        this.ruleBasedExtractor = ruleBasedExtractor;
        this.objectMapper = objectMapper;
        this.groqClientService = groqClientService;
        this.geminiLLMProvider = geminiLLMProvider;
        this.openAILLMProvider = openAILLMProvider;
        this.claudeLLMProvider = claudeLLMProvider;
    }

    public LlmExtractionService(
            RuleBasedCriteriaExtractor ruleBasedExtractor,
            ObjectMapper objectMapper,
            GroqClientService groqClientService
    ) {
        this(ruleBasedExtractor, objectMapper, groqClientService, null, null, null);
    }

    public LlmExtractionService(
            RuleBasedCriteriaExtractor ruleBasedExtractor,
            ObjectMapper objectMapper
    ) {
        this(ruleBasedExtractor, objectMapper, null, null, null, null);
    }

    private RestTemplate buildTimedRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = llmTimeoutMs > 0 ? llmTimeoutMs : 2500;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    public CriteresExtraitsDTO extractCriteria(String userMessage) {
        return extractCriteria(userMessage, null);
    }

    public record GenerationOutcome(String responseText, String motifSecours) {}

    public CriteresExtraitsDTO extractCriteria(String userMessage, CriteresExtraitsDTO previousContext) {
        // 1. Validation de sécurité immédiate (longueur <= 1000 + filtre Prompt Injection) avant tout appel réseau
        ruleBasedExtractor.validateAndSanitize(userMessage);

        // 2. Analyse déterministe de référence (pour détecter hors-sujet, intentions spéciales et compléments NLU)
        CriteresExtraitsDTO deterministic = ruleBasedExtractor.extractCriteria(userMessage);
        if (Boolean.TRUE.equals(deterministic.getHorsSujet())) {
            return deterministic;
        }
        String configFallbackMotif = resolveConfigFallbackMotif();
        if ("FAQ_VERIFICATION_JURIDIQUE".equals(deterministic.getIntentionSpeciale())) {
            if (configFallbackMotif == null) {
                // Intention juridique détectée localement pour garantir le routage, mais Gemini reste éligible pour la rédaction
                deterministic.setModeSecours(false);
                deterministic.setFournisseurIa(PROVIDER_GEMINI);
                deterministic.setMotifSecours(null);
                return deterministic;
            }
            return buildLocalFallback(deterministic, configFallbackMotif);
        }

        // 3. Politique RENDER-GEMINI-01 : Gemini est le SEUL fournisseur LLM externe autorisé (zéro appel Groq/OpenAI/Claude)
        if ("IA_DESACTIVEE".equals(configFallbackMotif)) {
            return buildLocalFallback(deterministic, "IA_DESACTIVEE");
        }

        String requestedProvider = getActivePrimaryProviderName();
        if ("FOURNISSEUR_NON_AUTORISE".equals(configFallbackMotif)) {
            if (!PROVIDER_LOCAL.equals(requestedProvider) && !"none".equals(requestedProvider) && !"disabled".equals(requestedProvider)) {
                log.warn("Fournisseur IA '{}' ignoré (RENDER-GEMINI-01 : seul Google Gemini est autorisé). Bascule directe sur le moteur local déterministe.",
                        requestedProvider);
            }
            return buildLocalFallback(deterministic, "FOURNISSEUR_NON_AUTORISE");
        }

        if (geminiLLMProvider != null && geminiLLMProvider.isConfigured()) {
            try {
                CriteresExtraitsDTO llmResult = geminiLLMProvider.extractCriteria(userMessage, previousContext);
                if (llmResult == null) {
                    llmResult = geminiLLMProvider.extractCriteria(userMessage);
                }
                if (llmResult != null) {
                    CriteresExtraitsDTO enriched = enrichLlmResultWithDeterministicRules(llmResult, deterministic);
                    enriched.setFournisseurIa(PROVIDER_GEMINI);
                    enriched.setModeSecours(false);
                    enriched.setMotifSecours(null);
                    return enriched;
                }
                return buildLocalFallback(deterministic, "REPONSE_INVALIDE");
            } catch (Exception e) {
                String motif = classifyFallbackReason(e);
                log.warn("Échec extraction Gemini (motif={}, exception={}) -> bascule directe sur le moteur local déterministe",
                        motif, e.getClass().getSimpleName());
                return buildLocalFallback(deterministic, motif);
            }
        }

        // 4. Support de compatibilité directe Gemini si apiKey est injectée sur LlmExtractionService (ex: LlmExtractionMockServerTest)
        if (geminiLLMProvider == null && apiKey != null && !apiKey.isBlank()) {
            try {
                CriteresExtraitsDTO legacyResult = attemptLegacyDirectExtractionWithSingleRetry(userMessage);
                legacyResult.setFournisseurIa(PROVIDER_GEMINI);
                legacyResult.setModeSecours(false);
                legacyResult.setMotifSecours(null);
                return enrichLlmResultWithDeterministicRules(legacyResult, deterministic);
            } catch (Exception e) {
                String motif = classifyFallbackReason(e);
                log.warn("Échec définitif de l'appel Gemini direct (motif={}, exception={}). Bascule sur le moteur local déterministe.",
                        motif, e.getClass().getSimpleName());
                return buildLocalFallback(deterministic, motif);
            }
        }

        // 5. Fallback déterministe local final (clé absente)
        return buildLocalFallback(deterministic, "CLE_ABSENTE");
    }

    /**
     * Génère une réponse conversationnelle uniquement via Google Gemini.
     * Retourne null si Gemini est désactivé, non configuré ou en erreur afin que IAService utilise le générateur déterministe local.
     */
    public String generateConversationalResponse(
            String userMessage,
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> annoncesTrouvees,
            String questionDeRelance
    ) {
        return generateConversationalResponseWithDiagnostic(userMessage, criteres, annoncesTrouvees, questionDeRelance).responseText();
    }

    public GenerationOutcome generateConversationalResponseWithDiagnostic(
            String userMessage,
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> annoncesTrouvees,
            String questionDeRelance
    ) {
        String configMotif = resolveConfigFallbackMotif();
        if (configMotif != null) {
            return new GenerationOutcome(null, configMotif);
        }
        try {
            String response = geminiLLMProvider.generateConversationalResponse(
                    userMessage,
                    criteres,
                    annoncesTrouvees,
                    questionDeRelance
            );
            if (response != null && !response.isBlank()) {
                return new GenerationOutcome(response, null);
            }
            return new GenerationOutcome(null, "REPONSE_INVALIDE");
        } catch (Exception e) {
            String motif = classifyFallbackReason(e);
            log.warn("Échec génération conversationnelle Gemini (motif={}, exception={}) -> bascule sur le générateur local déterministe",
                    motif, e.getClass().getSimpleName());
            return new GenerationOutcome(null, motif);
        }
    }

    public String classifyFallbackReason(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
                return httpEx.getStatusCode().value() == 429 ? "QUOTA" : "ERREUR_HTTP";
            }
            if (cursor instanceof org.springframework.web.client.ResourceAccessException
                    || cursor instanceof java.net.SocketTimeoutException) {
                return "TIMEOUT";
            }
            if (cursor instanceof IllegalStateException ise && ise.getMessage() != null) {
                String msg = ise.getMessage();
                if (msg.contains("Quota Gemini") || msg.contains("429")) {
                    return "QUOTA";
                }
                if (msg.contains("GEMINI_API_KEY") || msg.contains("n'est pas configurée")) {
                    return "CLE_ABSENTE";
                }
                if (msg.contains("Réponse") || msg.contains("Contenu textuel")) {
                    return "REPONSE_INVALIDE";
                }
            }
            if (cursor instanceof com.fasterxml.jackson.core.JsonProcessingException
                    || cursor instanceof IllegalArgumentException) {
                return "REPONSE_INVALIDE";
            }
            cursor = cursor.getCause();
        }
        return "ERREUR_HTTP";
    }

    /**
     * Détermine le motif de repli local lié à la configuration sans réaliser d'appel réseau.
     * Retourne {@code null} lorsque Google Gemini est actif et correctement configuré.
     */
    public String resolveConfigFallbackMotif() {
        if (!iaEnabled) {
            return "IA_DESACTIVEE";
        }
        if (!PROVIDER_GEMINI.equals(getActivePrimaryProviderName())) {
            return "FOURNISSEUR_NON_AUTORISE";
        }
        boolean hasProviderKey = geminiLLMProvider != null && geminiLLMProvider.isConfigured();
        boolean hasLegacyDirectKey = geminiLLMProvider == null && apiKey != null && !apiKey.isBlank();
        if (!hasProviderKey && !hasLegacyDirectKey) {
            return "CLE_ABSENTE";
        }
        return null;
    }

    public boolean isExternalGeminiActive() {
        return resolveConfigFallbackMotif() == null;
    }

    public boolean isAnyLlmProviderConfigured() {
        return isExternalGeminiActive();
    }

    public String getActivePrimaryProviderName() {
        return (provider != null && !provider.isBlank()) ? provider.trim().toLowerCase(Locale.ROOT) : PROVIDER_GEMINI;
    }

    public String getFallbackProviderName() {
        return PROVIDER_LOCAL;
    }

    private CriteresExtraitsDTO buildLocalFallback(CriteresExtraitsDTO deterministic, String motifSecours) {
        deterministic.setFournisseurIa("fallback-local");
        deterministic.setModeSecours(true);
        deterministic.setMotifSecours(motifSecours);
        return deterministic;
    }

    private CriteresExtraitsDTO enrichLlmResultWithDeterministicRules(
            CriteresExtraitsDTO llm,
            CriteresExtraitsDTO det
    ) {
        if (det == null) return llm;
        if (det.getCriteresASupprimer() != null && !det.getCriteresASupprimer().isEmpty()) {
            llm.setCriteresASupprimer(det.getCriteresASupprimer());
        }
        if (llm.getIntentionSpeciale() == null && det.getIntentionSpeciale() != null) {
            llm.setIntentionSpeciale(det.getIntentionSpeciale());
        }
        if (det.getDemandeQuestionsComplementaires() != null) {
            llm.setDemandeQuestionsComplementaires(det.getDemandeQuestionsComplementaires());
        }
        if (det.getBesoinIndecis() != null) {
            llm.setBesoinIndecis(det.getBesoinIndecis());
        }
        if (llm.getType() == null && det.getType() != null) {
            llm.setType(det.getType());
        }
        if (llm.getModeTransaction() == null && det.getModeTransaction() != null) {
            llm.setModeTransaction(det.getModeTransaction());
        }
        if (llm.getBudgetMax() == null && det.getBudgetMax() != null) {
            llm.setBudgetMax(det.getBudgetMax());
        }
        if (llm.getBudgetMin() == null && det.getBudgetMin() != null) {
            llm.setBudgetMin(det.getBudgetMin());
        }
        if (llm.getQuartier() == null && det.getQuartier() != null) {
            llm.setQuartier(det.getQuartier());
        }
        if ((llm.getQuartiers() == null || llm.getQuartiers().isEmpty()) && det.getQuartiers() != null && !det.getQuartiers().isEmpty()) {
            llm.setQuartiers(det.getQuartiers());
        }
        if (llm.getVille() == null && det.getVille() != null) {
            llm.setVille(det.getVille());
        }
        if (llm.getEtage() == null && det.getEtage() != null) {
            llm.setEtage(det.getEtage());
        }
        if (llm.getAscenseur() == null && det.getAscenseur() != null) {
            llm.setAscenseur(det.getAscenseur());
        }
        if (llm.getSuperficieMin() == null && det.getSuperficieMin() != null) {
            llm.setSuperficieMin(det.getSuperficieMin());
        }
        if (llm.getSuperficieMax() == null && det.getSuperficieMax() != null) {
            llm.setSuperficieMax(det.getSuperficieMax());
        }
        if (llm.getAnimauxAcceptes() == null && det.getAnimauxAcceptes() != null) {
            llm.setAnimauxAcceptes(det.getAnimauxAcceptes());
        }
        if (llm.getNombreChambres() == null && det.getNombreChambres() != null) {
            llm.setNombreChambres(det.getNombreChambres());
        }
        if ((llm.getEquipements() == null || llm.getEquipements().isEmpty()) && det.getEquipements() != null && !det.getEquipements().isEmpty()) {
            llm.setEquipements(det.getEquipements());
        }
        if ((llm.getProximites() == null || llm.getProximites().isEmpty()) && det.getProximites() != null && !det.getProximites().isEmpty()) {
            llm.setProximites(det.getProximites());
        }
        llm.setPreferencesSouples(mergeStringLists(llm.getPreferencesSouples(), det.getPreferencesSouples()));
        llm.setAttentesComplementaires(mergeStringLists(llm.getAttentesComplementaires(), det.getAttentesComplementaires()));
        if (!"QUESTION_GENERALE".equals(llm.getIntentionSpeciale())
                && !"FAQ_DOCUMENTS".equals(llm.getIntentionSpeciale())) {
            llm.setPrecisionsAClarifier(mergeStringLists(llm.getPrecisionsAClarifier(), det.getPrecisionsAClarifier()));
            if (llm.getQuestionDeRelance() == null && det.getQuestionDeRelance() != null) {
                llm.setQuestionDeRelance(det.getQuestionDeRelance());
            }
        }
        return llm;
    }

    private List<String> mergeStringLists(List<String> primary, List<String> fallback) {
        if ((primary == null || primary.isEmpty()) && (fallback == null || fallback.isEmpty())) {
            return null;
        }
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (primary != null) merged.addAll(primary);
        if (fallback != null) merged.addAll(fallback);
        return new java.util.ArrayList<>(merged);
    }

    private CriteresExtraitsDTO attemptLegacyDirectExtractionWithSingleRetry(String userMessage) throws Exception {
        try {
            return callLegacyLlmApi(userMessage);
        } catch (org.springframework.web.client.ResourceAccessException timeoutEx) {
            throw timeoutEx;
        } catch (Exception firstEx) {
            log.warn("Premier essai LLM direct en échec ({}). Tentative unique de retry...", firstEx.getMessage());
            return callLegacyLlmApi(userMessage);
        }
    }

    private CriteresExtraitsDTO callLegacyLlmApi(String userMessage) throws Exception {
        String systemPrompt = """
                Tu es un assistant immobilier au Sénégal (E-Courtier).
                Analyse UNIQUEMENT le message client délimité par <user_query_untrusted> et extrais les critères de recherche immobilière au format JSON strict, sans texte autour :
                {
                  "budgetMin": number ou null,
                  "budgetMax": number ou null,
                  "type": "STUDIO" | "APPARTEMENT" | "VILLA" | "CHAMBRE" | null,
                  "modeTransaction": "LOCATION" | "VENTE" | null,
                  "quartier": string ou null,
                  "ville": string ou null,
                  "nombrePieces": integer ou null,
                  "meuble": boolean ou null,
                  "confiance": number entre 0.0 et 1.0,
                  "questionDeRelance": string ou null
                }
                """;

        String sandboxedUserMessage = "<user_query_untrusted>\n" + userMessage + "\n</user_query_untrusted>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        String endpoint = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl
                : "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", systemPrompt + "\n\nMessage client : " + sandboxedUserMessage)))
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = buildTimedRestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Réponse non-2xx du fournisseur Gemini : " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Réponse Gemini sans candidats exploitables");
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        StringBuilder textBuilder = new StringBuilder();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                String partText = part.path("text").asText("");
                if (!partText.isBlank()) {
                    if (!textBuilder.isEmpty()) {
                        textBuilder.append('\n');
                    }
                    textBuilder.append(partText);
                }
            }
        }
        String rawText = textBuilder.toString().trim();
        if (rawText.isEmpty()) {
            throw new IllegalStateException("Réponse Gemini vide hors blocs de réflexion");
        }

        return parseAndValidateStrictDto(rawText);
    }

    private CriteresExtraitsDTO parseAndValidateStrictDto(String jsonText) throws Exception {
        String cleaned = jsonText.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        JsonNode node = objectMapper.readTree(cleaned);

        CriteresExtraitsDTO.CriteresExtraitsDTOBuilder builder = CriteresExtraitsDTO.builder();

        if (node.hasNonNull("budgetMin") && node.get("budgetMin").isNumber()) {
            BigDecimal val = node.get("budgetMin").decimalValue();
            if (val.compareTo(BigDecimal.ZERO) > 0) builder.budgetMin(val);
        }
        if (node.hasNonNull("budgetMax") && node.get("budgetMax").isNumber()) {
            BigDecimal val = node.get("budgetMax").decimalValue();
            if (val.compareTo(BigDecimal.ZERO) > 0) builder.budgetMax(val);
        }
        if (node.hasNonNull("type")) {
            try {
                builder.type(TypeBien.valueOf(node.get("type").asText().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (node.hasNonNull("modeTransaction")) {
            try {
                builder.modeTransaction(ModeTransaction.valueOf(node.get("modeTransaction").asText().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (node.hasNonNull("quartier") && !node.get("quartier").asText().isBlank()) {
            builder.quartier(node.get("quartier").asText().trim());
        }
        if (node.hasNonNull("ville") && !node.get("ville").asText().isBlank()) {
            builder.ville(node.get("ville").asText().trim());
        }
        if (node.hasNonNull("nombrePieces") && node.get("nombrePieces").isInt()) {
            int pieces = node.get("nombrePieces").asInt();
            if (pieces > 0 && pieces <= 30) builder.nombrePieces(pieces);
        }
        if (node.hasNonNull("meuble") && node.get("meuble").isBoolean()) {
            builder.meuble(node.get("meuble").asBoolean());
        }
        if (node.hasNonNull("confiance") && node.get("confiance").isNumber()) {
            builder.confiance(node.get("confiance").asDouble());
        } else {
            builder.confiance(0.8);
        }
        if (node.hasNonNull("questionDeRelance") && !node.get("questionDeRelance").asText().isBlank()) {
            builder.questionDeRelance(node.get("questionDeRelance").asText().trim());
        }

        return builder.build();
    }
}
