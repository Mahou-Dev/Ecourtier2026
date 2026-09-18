package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.repository.AnnonceSpecification;
import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.ConversationHistoryResponseDTO;
import com.ecourtier.domain.ia.dto.CriteresExtraitsDTO;
import com.ecourtier.domain.ia.dto.MessageIaResponseDTO;
import com.ecourtier.domain.ia.dto.MessageItemDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import com.ecourtier.domain.ia.entity.MessageIAEntity;
import com.ecourtier.domain.ia.entity.RoleMessageIA;
import com.ecourtier.domain.ia.repository.ConversationIARepository;
import com.ecourtier.domain.ia.repository.MessageIARepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IAService {

    private final ConversationIARepository conversationIARepository;
    private final MessageIARepository messageIARepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AnnonceRepository annonceRepository;
    private final AnnonceMapper annonceMapper;
    private final LlmExtractionService llmExtractionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail) {
        UtilisateurEntity user = null;
        if (userEmail != null && !userEmail.trim().isEmpty() && !"anonymousUser".equals(userEmail)) {
            user = utilisateurRepository.findByEmail(userEmail).orElse(null);
        }

        ConversationIAEntity conv = ConversationIAEntity.builder()
                .utilisateur(user)
                .dateCreation(java.time.LocalDateTime.now())
                .build();

        ConversationIAEntity saved = conversationIARepository.save(conv);
        return ConversationCreateResponseDTO.builder()
                .conversationId(saved.getId())
                .dateCreation(saved.getDateCreation() != null ? saved.getDateCreation() : conv.getDateCreation())
                .build();
    }

    @Transactional
    public MessageIaResponseDTO envoyerMessage(UUID conversationId, MessageUserRequestDTO request, String userEmail) {
        ConversationIAEntity conv = conversationIARepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

        // 1. Extraire les critères du message actuel
        CriteresExtraitsDTO criteres = llmExtractionService.extractCriteria(request.getMessage());

        // Fusionner avec l'historique de la conversation si disponible
        List<MessageIAEntity> historyMessages = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conversationId);
        for (MessageIAEntity prevMsg : historyMessages) {
            CriteresExtraitsDTO prevCrit = deserializeCriteres(prevMsg.getCriteresExtraits());
            if (prevCrit != null) {
                if (criteres.getQuartier() == null) criteres.setQuartier(prevCrit.getQuartier());
                if (criteres.getVille() == null) criteres.setVille(prevCrit.getVille());
                if (criteres.getType() == null) criteres.setType(prevCrit.getType());
                if (criteres.getModeTransaction() == null) criteres.setModeTransaction(prevCrit.getModeTransaction());
                if (criteres.getBudgetMax() == null) criteres.setBudgetMax(prevCrit.getBudgetMax());
                if (criteres.getBudgetMin() == null) criteres.setBudgetMin(prevCrit.getBudgetMin());
                if (criteres.getNombrePieces() == null) criteres.setNombrePieces(prevCrit.getNombrePieces());
                if (criteres.getMeuble() == null) criteres.setMeuble(prevCrit.getMeuble());
            }
        }

        // Si le budget est présent (directement ou via fusion), annuler la question de relance
        if (criteres.getBudgetMax() != null || criteres.getBudgetMin() != null) {
            criteres.setQuestionDeRelance(null);
        }

        String criteresJson = serializeCriteres(criteres);

        // 2. Enregistrer le message de l'utilisateur
        MessageIAEntity userMessage = MessageIAEntity.builder()
                .conversation(conv)
                .role(RoleMessageIA.UTILISATEUR)
                .contenu(request.getMessage())
                .criteresExtraits(criteresJson)
                .dateEnvoi(java.time.LocalDateTime.now())
                .build();
        messageIARepository.save(userMessage);

        List<AnnonceResponseDTO> annoncesSuggeres = new ArrayList<>();
        String textReponse;

        // 3. Traiter la relance ou chercher dans la base
        if (criteres.getQuestionDeRelance() != null && !criteres.getQuestionDeRelance().trim().isEmpty()) {
            textReponse = "Pour affiner ma recherche, " + criteres.getQuestionDeRelance();
        } else {
            java.math.BigDecimal budgetMin = (criteres.getBudgetMin() != null && criteres.getBudgetMin().compareTo(java.math.BigDecimal.ZERO) > 0) ? criteres.getBudgetMin() : null;
            java.math.BigDecimal budgetMax = (criteres.getBudgetMax() != null && criteres.getBudgetMax().compareTo(java.math.BigDecimal.ZERO) > 0) ? criteres.getBudgetMax() : null;
            Double superficieMin = (criteres.getSuperficieMin() != null && criteres.getSuperficieMin() > 0) ? criteres.getSuperficieMin() : null;
            Integer nombrePieces = (criteres.getNombrePieces() != null && criteres.getNombrePieces() > 0) ? criteres.getNombrePieces() : null;

            Specification<AnnonceEntity> spec = AnnonceSpecification.filterAnnonces(
                    budgetMin,
                    budgetMax,
                    criteres.getType(),
                    criteres.getModeTransaction(),
                    superficieMin,
                    nombrePieces,
                    criteres.getQuartier(),
                    criteres.getVille(),
                    criteres.getMeuble(),
                    criteres.getAnimauxAcceptes(),
                    null, // statutVerification optionnel
                    null  // proximite optionnelle
            );

            Page<AnnonceEntity> annoncesPage = annonceRepository.findAll(spec, PageRequest.of(0, 5));
            annoncesSuggeres = annoncesPage.getContent().stream()
                    .map(annonceMapper::toResponseDTO)
                    .toList();

            if (annoncesSuggeres.isEmpty()) {
                textReponse = "J'ai bien analysé votre demande. Malheureusement, aucune annonce correspondant exactement à ces critères n'a été trouvée. Vous pouvez essayer d'élargir votre zone ou votre budget.";
            } else {
                StringBuilder sb = new StringBuilder("J'ai trouvé ")
                        .append(annoncesSuggeres.size())
                        .append(" annonce(s) correspondant à votre recherche");
                if (criteres.getQuartier() != null) {
                    sb.append(" à ").append(criteres.getQuartier());
                }
                if (criteres.getBudgetMax() != null) {
                    sb.append(" pour un budget max de ").append(criteres.getBudgetMax()).append(" FCFA");
                }
                sb.append(" :");
                textReponse = sb.toString();
            }
        }

        // 4. Enregistrer le message de l'assistant
        java.time.LocalDateTime nowAssistant = java.time.LocalDateTime.now();
        MessageIAEntity assistantMessage = MessageIAEntity.builder()
                .conversation(conv)
                .role(RoleMessageIA.ASSISTANT)
                .contenu(textReponse)
                .criteresExtraits(criteresJson)
                .dateEnvoi(nowAssistant)
                .build();
        MessageIAEntity savedAssistant = messageIARepository.save(assistantMessage);

        return MessageIaResponseDTO.builder()
                .id(savedAssistant.getId())
                .reponse(textReponse)
                .annoncesSuggeres(annoncesSuggeres)
                .criteresExtraits(criteres)
                .questionDeRelance(criteres.getQuestionDeRelance())
                .dateEnvoi(savedAssistant.getDateEnvoi() != null ? savedAssistant.getDateEnvoi() : nowAssistant)
                .build();
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(UUID conversationId) {
        ConversationIAEntity conv = conversationIARepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

        List<MessageIAEntity> messages = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conversationId);

        List<MessageItemDTO> items = messages.stream().map(m -> MessageItemDTO.builder()
                .id(m.getId())
                .role(m.getRole())
                .contenu(m.getContenu())
                .criteresExtraits(deserializeCriteres(m.getCriteresExtraits()))
                .dateEnvoi(m.getDateEnvoi())
                .build()
        ).toList();

        return ConversationHistoryResponseDTO.builder()
                .conversationId(conv.getId())
                .dateCreation(conv.getDateCreation())
                .messages(items)
                .build();
    }

    private String serializeCriteres(CriteresExtraitsDTO dto) {
        if (dto == null) return null;
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private CriteresExtraitsDTO deserializeCriteres(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, CriteresExtraitsDTO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
