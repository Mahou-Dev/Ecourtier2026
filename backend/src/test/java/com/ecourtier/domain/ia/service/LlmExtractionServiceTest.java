package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmExtractionServiceTest {

    private LlmExtractionService llmExtractionService;
    private RuleBasedCriteriaExtractor ruleBasedExtractor;

    @BeforeEach
    void setUp() {
        ruleBasedExtractor = new RuleBasedCriteriaExtractor();
        llmExtractionService = new LlmExtractionService(ruleBasedExtractor, new ObjectMapper());
    }

    @Test
    @DisplayName("Point 2 : Retombée propre sur le moteur de règles quand aucune clé LLM n'est fournie")
    void testFallbackQuandCleAbsente() {
        ReflectionTestUtils.setField(llmExtractionService, "apiKey", "");

        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("Studio à Boucotte pour 95000 FCFA");

        assertNotNull(result);
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("95000"), result.getBudgetMax());
    }

    @Test
    @DisplayName("Groq : Retombée sur le moteur de règles quand la clé Groq n'est pas configurée")
    void testFallbackQuandGroqNonConfigure() {
        ReflectionTestUtils.setField(llmExtractionService, "provider", "groq");

        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("Studio à Boucotte pour 95000 FCFA");

        assertNotNull(result);
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("95000"), result.getBudgetMax());
    }

    @Test
    @DisplayName("Groq : Retombée sur le moteur de règles en cas d'échec du client Groq")
    void testFallbackQuandGroqEchoue() throws Exception {
        com.ecourtier.domain.ia.client.GroqClientService mockGroq = org.mockito.Mockito.mock(com.ecourtier.domain.ia.client.GroqClientService.class);
        org.mockito.Mockito.when(mockGroq.isConfigured()).thenReturn(true);
        org.mockito.Mockito.when(mockGroq.extractCriteria(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("Simulated Groq Failure"));

        LlmExtractionService service = new LlmExtractionService(ruleBasedExtractor, new ObjectMapper(), mockGroq);
        ReflectionTestUtils.setField(service, "provider", "groq");

        CriteresExtraitsDTO result = service.extractCriteria("3 pièces à Nema, budget 150000 FCFA, meublé");

        assertNotNull(result);
        assertEquals("Nema", result.getQuartier());
        assertEquals(new BigDecimal("150000"), result.getBudgetMax());
    }

    @Test
    @DisplayName("Groq : Retour direct des critères quand l'appel Groq réussit")
    void testExtractionGroqReussie() throws Exception {
        com.ecourtier.domain.ia.client.GroqClientService mockGroq = org.mockito.Mockito.mock(com.ecourtier.domain.ia.client.GroqClientService.class);
        org.mockito.Mockito.when(mockGroq.isConfigured()).thenReturn(true);

        CriteresExtraitsDTO mockRes = CriteresExtraitsDTO.builder()
                .ville("Ziguinchor")
                .quartier("Boucotte")
                .budgetMax(new BigDecimal("80000"))
                .type(com.ecourtier.domain.annonce.entity.TypeBien.STUDIO)
                .confiance(0.95)
                .build();

        org.mockito.Mockito.when(mockGroq.extractCriteria(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mockRes);

        LlmExtractionService service = new LlmExtractionService(ruleBasedExtractor, new ObjectMapper(), mockGroq);
        ReflectionTestUtils.setField(service, "provider", "groq");

        CriteresExtraitsDTO result = service.extractCriteria("chambre à Boucotte 80000");

        assertNotNull(result);
        assertEquals("Ziguinchor", result.getVille());
        assertEquals("Boucotte", result.getQuartier());
        assertEquals(new BigDecimal("80000"), result.getBudgetMax());
        assertEquals(0.95, result.getConfiance());
    }
}
