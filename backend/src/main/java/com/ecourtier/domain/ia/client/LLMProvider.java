package com.ecourtier.domain.ia.client;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;

import java.util.List;

/**
 * Abstraction multi-fournisseurs LLM pour l'assistant conversationnel immobilier E-Courtier Sénégal.
 * Permet d'interchanger ou chaîner Gemini (principal), Groq (secours), OpenAI et Anthropic Claude
 * avant retombée déterministe sur le moteur de règles local.
 */
public interface LLMProvider {

    /**
     * Identifiant technique court du fournisseur (ex: "gemini", "groq", "openai", "claude").
     */
    String getProviderName();

    /**
     * Modèle LLM effectif utilisé par ce fournisseur.
     */
    String getEffectiveModel();

    /**
     * Indique si le fournisseur dispose d'une clé API et d'une configuration valides.
     */
    boolean isConfigured();

    /**
     * Extrait les critères immobiliers structurés à partir du message utilisateur.
     */
    CriteresExtraitsDTO extractCriteria(String userMessage);

    /**
     * Extrait les critères immobiliers en tenant compte du contexte conversationnel précédent.
     */
    default CriteresExtraitsDTO extractCriteria(String userMessage, CriteresExtraitsDTO previousContext) {
        return extractCriteria(userMessage);
    }

    /**
     * Génère une réponse conversationnelle naturelle ancrée exclusivement sur les annonces réelles trouvées en base.
     */
    String generateConversationalResponse(
            String userMessage,
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> annoncesTrouvees,
            String questionDeRelance
    );
}
