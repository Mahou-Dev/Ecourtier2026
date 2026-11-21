package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.ia.client.ClaudeLLMProvider;
import com.ecourtier.domain.ia.client.GeminiLLMProvider;
import com.ecourtier.domain.ia.client.GroqClientService;
import com.ecourtier.domain.ia.client.OpenAILLMProvider;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmExtractionServiceTest {

    private LlmExtractionService llmExtractionService;
    private RuleBasedCriteriaExtractor ruleBasedExtractor;

    @BeforeEach
    void setUp() {
        ruleBasedExtractor = new RuleBasedCriteriaExtractor();
        llmExtractionService = new LlmExtractionService(ruleBasedExtractor, new ObjectMapper());
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : Retombée propre sur le moteur local quand aucune clé Gemini n'est fournie")
    void testFallbackQuandCleAbsente() {
        ReflectionTestUtils.setField(llmExtractionService, "apiKey", "");

        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("Studio à Boucotte pour 95000 FCFA");

        assertNotNull(result);
        assertEquals("fallback-local", result.getFournisseurIa());
        assertEquals(Boolean.TRUE, result.getModeSecours());
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("95000"), result.getBudgetMax());
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : Fallback local direct sans jamais appeler Groq, OpenAI ni Claude quand Gemini est non configuré")
    void testFallbackQuandGeminiNonConfigure() {
        GeminiLLMProvider mockGemini = Mockito.mock(GeminiLLMProvider.class);
        GroqClientService mockGroq = Mockito.mock(GroqClientService.class);
        OpenAILLMProvider mockOpenAi = Mockito.mock(OpenAILLMProvider.class);
        ClaudeLLMProvider mockClaude = Mockito.mock(ClaudeLLMProvider.class);

        when(mockGemini.isConfigured()).thenReturn(false);
        when(mockGroq.isConfigured()).thenReturn(true);
        when(mockOpenAi.isConfigured()).thenReturn(true);
        when(mockClaude.isConfigured()).thenReturn(true);

        LlmExtractionService service = new LlmExtractionService(
                ruleBasedExtractor, new ObjectMapper(), mockGroq, mockGemini, mockOpenAi, mockClaude
        );

        CriteresExtraitsDTO result = service.extractCriteria("Studio à Boucotte pour 95000 FCFA");

        assertNotNull(result);
        assertEquals("fallback-local", result.getFournisseurIa());
        assertEquals(Boolean.TRUE, result.getModeSecours());
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("95000"), result.getBudgetMax());
        verifyNoInteractions(mockGroq, mockOpenAi, mockClaude);
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : Fallback local direct en cas d'échec Gemini sans jamais appeler Groq/OpenAI/Claude")
    void testFallbackQuandGeminiEchoue() {
        GeminiLLMProvider mockGemini = Mockito.mock(GeminiLLMProvider.class);
        GroqClientService mockGroq = Mockito.mock(GroqClientService.class);
        OpenAILLMProvider mockOpenAi = Mockito.mock(OpenAILLMProvider.class);
        ClaudeLLMProvider mockClaude = Mockito.mock(ClaudeLLMProvider.class);

        when(mockGemini.isConfigured()).thenReturn(true);
        when(mockGemini.extractCriteria(anyString(), any())).thenThrow(new RuntimeException("Simulated Gemini 429"));
        when(mockGroq.isConfigured()).thenReturn(true);

        LlmExtractionService service = new LlmExtractionService(
                ruleBasedExtractor, new ObjectMapper(), mockGroq, mockGemini, mockOpenAi, mockClaude
        );

        CriteresExtraitsDTO result = service.extractCriteria("3 pièces à Nema, budget 150000 FCFA, meublé");

        assertNotNull(result);
        assertEquals("fallback-local", result.getFournisseurIa());
        assertEquals(Boolean.TRUE, result.getModeSecours());
        assertEquals("Nema", result.getQuartier());
        assertEquals(new BigDecimal("150000"), result.getBudgetMax());
        verifyNoInteractions(mockGroq, mockOpenAi, mockClaude);
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : Retour direct des critères via Google Gemini sans appeler Groq/OpenAI/Claude")
    void testExtractionGeminiReussie() {
        GeminiLLMProvider mockGemini = Mockito.mock(GeminiLLMProvider.class);
        GroqClientService mockGroq = Mockito.mock(GroqClientService.class);
        OpenAILLMProvider mockOpenAi = Mockito.mock(OpenAILLMProvider.class);
        ClaudeLLMProvider mockClaude = Mockito.mock(ClaudeLLMProvider.class);

        when(mockGemini.isConfigured()).thenReturn(true);
        CriteresExtraitsDTO mockRes = CriteresExtraitsDTO.builder()
                .ville("Ziguinchor")
                .quartier("Boucotte")
                .budgetMax(new BigDecimal("80000"))
                .type(TypeBien.CHAMBRE)
                .modeTransaction(ModeTransaction.LOCATION)
                .confiance(0.95)
                .build();
        when(mockGemini.extractCriteria(anyString(), any())).thenReturn(mockRes);

        LlmExtractionService service = new LlmExtractionService(
                ruleBasedExtractor, new ObjectMapper(), mockGroq, mockGemini, mockOpenAi, mockClaude
        );

        CriteresExtraitsDTO result = service.extractCriteria("chambre à Boucotte 80000");

        assertNotNull(result);
        assertEquals("gemini", result.getFournisseurIa());
        assertEquals(Boolean.FALSE, result.getModeSecours());
        assertEquals("Ziguinchor", result.getVille());
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("80000"), result.getBudgetMax());
        assertEquals(0.95, result.getConfiance());
        verifyNoInteractions(mockGroq, mockOpenAi, mockClaude);
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : Un provider tiers hérité (groq/openai/claude) est ignoré au profit du moteur local sans aucun appel externe")
    void testFournisseurTiersConfigureIgnoreSansAppel() {
        GeminiLLMProvider mockGemini = Mockito.mock(GeminiLLMProvider.class);
        GroqClientService mockGroq = Mockito.mock(GroqClientService.class);
        OpenAILLMProvider mockOpenAi = Mockito.mock(OpenAILLMProvider.class);
        ClaudeLLMProvider mockClaude = Mockito.mock(ClaudeLLMProvider.class);

        when(mockGroq.isConfigured()).thenReturn(true);
        LlmExtractionService service = new LlmExtractionService(
                ruleBasedExtractor, new ObjectMapper(), mockGroq, mockGemini, mockOpenAi, mockClaude
        );
        ReflectionTestUtils.setField(service, "provider", "groq");

        CriteresExtraitsDTO result = service.extractCriteria("Studio à Boucotte pour 95000 FCFA");

        assertEquals("fallback-local", result.getFournisseurIa());
        assertEquals(Boolean.TRUE, result.getModeSecours());
        assertNull(service.generateConversationalResponse("test", result, List.of(), null));
        verifyNoInteractions(mockGroq, mockOpenAi, mockClaude, mockGemini);
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : generateConversationalResponse n'appelle que Gemini et retourne null si indisponible")
    void testGenerateConversationalResponseGeminiUniquement() {
        GeminiLLMProvider mockGemini = Mockito.mock(GeminiLLMProvider.class);
        GroqClientService mockGroq = Mockito.mock(GroqClientService.class);
        OpenAILLMProvider mockOpenAi = Mockito.mock(OpenAILLMProvider.class);
        ClaudeLLMProvider mockClaude = Mockito.mock(ClaudeLLMProvider.class);

        when(mockGemini.isConfigured()).thenReturn(true);
        when(mockGemini.generateConversationalResponse(anyString(), any(), any(), any()))
                .thenReturn("Voici notre sélection vérifiée à Dakar.");

        LlmExtractionService service = new LlmExtractionService(
                ruleBasedExtractor, new ObjectMapper(), mockGroq, mockGemini, mockOpenAi, mockClaude
        );

        String ok = service.generateConversationalResponse("villa Dakar", CriteresExtraitsDTO.builder().build(), List.of(), null);
        assertEquals("Voici notre sélection vérifiée à Dakar.", ok);
        verify(mockGemini).generateConversationalResponse(anyString(), any(), any(), any());
        verifyNoInteractions(mockGroq, mockOpenAi, mockClaude);
    }
}
