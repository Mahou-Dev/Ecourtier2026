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
    @DisplayName("Point 2 & 3 : Simulation d'échec/latence LLM avec mesure du temps total < 3s")
    void testMesureTempsEtFallback() {
        ReflectionTestUtils.setField(llmExtractionService, "apiKey", "dummy-invalid-key");
        ReflectionTestUtils.setField(llmExtractionService, "provider", "claude");

        long start = System.currentTimeMillis();
        CriteresExtraitsDTO result = llmExtractionService.extractCriteria("3 pièces à Nema, budget 150000 FCFA, meublé");
        long duration = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertEquals("Nema", result.getQuartier());
        assertEquals(new BigDecimal("150000"), result.getBudgetMax());
        assertTrue(duration < 3000, "Le temps de réponse avec fallback doit rester < 3000ms (obtenu: " + duration + "ms)");
    }
}
