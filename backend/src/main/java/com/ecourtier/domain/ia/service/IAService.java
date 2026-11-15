package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.repository.AnnonceSpecification;
import com.ecourtier.domain.ia.client.GroqClientService;
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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IAService {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    private final ConversationIARepository conversationIARepository;
    private final MessageIARepository messageIARepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AnnonceRepository annonceRepository;
    private final AnnonceMapper annonceMapper;
    private final LlmExtractionService llmExtractionService;
    private final ObjectMapper objectMapper;
    private final GroqClientService groqClientService;

    @Autowired(required = false)
    private Clock clock = Clock.system(DAKAR_ZONE);

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(DAKAR_ZONE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock != null ? clock : Clock.system(DAKAR_ZONE));
    }

    public static final String IA_SESSION_COOKIE_NAME = "ecourtier_ia_session";
    public static final String IA_SESSION_HEADER_NAME = "X-IA-Session-Token";
    public static final int IA_SESSION_DURATION_DAYS = 7;

    public static String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hashToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible", e);
        }
    }

    public static String extractSessionToken(HttpServletRequest request) {
        if (request == null) return null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (IA_SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    String val = cookie.getValue();
                    if (val != null && !val.isBlank()) {
                        return val.trim();
                    }
                }
            }
        }
        String headerToken = request.getHeader(IA_SESSION_HEADER_NAME);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }
        return null;
    }

    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail) {
        return creerConversation(userEmail, (HttpServletRequest) null, (HttpServletResponse) null);
    }

    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail, String sessionToken) {
        return creerConversationInternal(userEmail, sessionToken, null, false);
    }

    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail, HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = extractSessionToken(request);
        return creerConversationInternal(userEmail, sessionToken, response, request != null && request.isSecure());
    }

    private ConversationCreateResponseDTO creerConversationInternal(String userEmail, String sessionToken, HttpServletResponse response, boolean isSecure) {
        UtilisateurEntity user = null;
        boolean isAuthentifie = (userEmail != null && !userEmail.trim().isEmpty() && !"anonymousUser".equals(userEmail));
        if (isAuthentifie) {
            user = utilisateurRepository.findByEmail(userEmail).orElse(null);
        }

        String sessionTokenHash = null;
        LocalDateTime expiration = null;

        if (user == null) {
            if (sessionToken == null || sessionToken.isBlank()) {
                sessionToken = generateSecureToken();
            }

            if (response != null) {
                ResponseCookie cookie = ResponseCookie.from(IA_SESSION_COOKIE_NAME, sessionToken)
                        .httpOnly(true)
                        .secure(isSecure)
                        .path("/api/v1/ia")
                        .maxAge(Duration.ofDays(IA_SESSION_DURATION_DAYS))
                        .sameSite("Lax")
                        .build();
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            }

            sessionTokenHash = hashToken(sessionToken);
            expiration = now().plusDays(IA_SESSION_DURATION_DAYS);
        }

        ConversationIAEntity conv = ConversationIAEntity.builder()
                .utilisateur(user)
                .sessionTokenHash(sessionTokenHash)
                .sessionExpiration(expiration)
                .dateCreation(now())
                .build();

        ConversationIAEntity saved = conversationIARepository.save(conv);
        return ConversationCreateResponseDTO.builder()
                .conversationId(saved.getId())
                .dateCreation(saved.getDateCreation() != null ? saved.getDateCreation() : conv.getDateCreation())
                .build();
    }

    @Transactional
    public MessageIaResponseDTO envoyerMessage(UUID conversationId, MessageUserRequestDTO request, String userEmail) {
        return envoyerMessage(conversationId, request, userEmail, (String) null);
    }

    @Transactional
    public MessageIaResponseDTO envoyerMessage(UUID conversationId, MessageUserRequestDTO request, String userEmail, HttpServletRequest httpRequest) {
        return envoyerMessage(conversationId, request, userEmail, extractSessionToken(httpRequest));
    }

    @Transactional
    public MessageIaResponseDTO envoyerMessage(UUID conversationId, MessageUserRequestDTO request, String userEmail, String sessionToken) {
        ConversationIAEntity conv = conversationIARepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

        verifierAccesConversation(conv, userEmail, sessionToken);

        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne peut pas être vide");
        }
        if (request.getMessage().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne doit pas dépasser 500 caractères");
        }

        long messageCount = messageIARepository.countByConversationId(conversationId);
        if (messageCount >= 60) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cette conversation a atteint la limite maximale de messages. Veuillez démarrer une nouvelle conversation."
            );
        }

        // 1. Extraire les critères du message actuel
        CriteresExtraitsDTO criteres = llmExtractionService.extractCriteria(request.getMessage());

        // Fusionner avec les critères cumulés du dernier message de la conversation
        List<MessageIAEntity> historyMessages = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conversationId);
        CriteresExtraitsDTO dernierCrit = null;
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            CriteresExtraitsDTO c = deserializeCriteres(historyMessages.get(i).getCriteresExtraits());
            if (c != null) {
                dernierCrit = c;
                break;
            }
        }

        if (dernierCrit != null) {
            if (criteres.getBudgetMin() == null) criteres.setBudgetMin(dernierCrit.getBudgetMin());
            if (criteres.getBudgetMax() == null) criteres.setBudgetMax(dernierCrit.getBudgetMax());
            if (criteres.getType() == null) criteres.setType(dernierCrit.getType());
            if (criteres.getModeTransaction() == null) criteres.setModeTransaction(dernierCrit.getModeTransaction());
            if (criteres.getQuartier() == null) criteres.setQuartier(dernierCrit.getQuartier());
            if (criteres.getVille() == null) criteres.setVille(dernierCrit.getVille());
            if (criteres.getNombrePieces() == null) criteres.setNombrePieces(dernierCrit.getNombrePieces());
            if (criteres.getEtage() == null) criteres.setEtage(dernierCrit.getEtage());
            if (criteres.getMeuble() == null) criteres.setMeuble(dernierCrit.getMeuble());
            if (criteres.getAnimauxAcceptes() == null) criteres.setAnimauxAcceptes(dernierCrit.getAnimauxAcceptes());
            if (criteres.getSuperficieMin() == null) criteres.setSuperficieMin(dernierCrit.getSuperficieMin());
        }

        // Gestion de la question de relance / clarification :
        // Une question reste active tant que le critère auquel elle correspond n'est pas résolu.
        String activeQuestion = criteres.getQuestionDeRelance();
        if ((activeQuestion == null || activeQuestion.isBlank()) && dernierCrit != null && dernierCrit.getQuestionDeRelance() != null) {
            activeQuestion = dernierCrit.getQuestionDeRelance();
        }

        if (activeQuestion != null && !activeQuestion.isBlank()) {
            if (estQuestionResolue(activeQuestion, criteres)) {
                criteres.setQuestionDeRelance(null);
            } else {
                criteres.setQuestionDeRelance(activeQuestion);
            }
        }

        String criteresJson = serializeCriteres(criteres);

        // 2. Enregistrer le message de l'utilisateur
        MessageIAEntity userMessage = MessageIAEntity.builder()
                .conversation(conv)
                .role(RoleMessageIA.UTILISATEUR)
                .contenu(request.getMessage())
                .criteresExtraits(criteresJson)
                .dateEnvoi(now())
                .build();
        messageIARepository.save(userMessage);

        List<AnnonceResponseDTO> annoncesSuggeres = new ArrayList<>();
        String textReponse = null;

        // 3. Traiter la relance ou chercher dans la base
        if (criteres.getQuestionDeRelance() != null && !criteres.getQuestionDeRelance().trim().isEmpty()) {
            if (criteres.getConfiance() != null && criteres.getConfiance() == 0.0) {
                // Demande hors domaine / recentrage
                textReponse = criteres.getQuestionDeRelance();
            } else {
                textReponse = "Pour affiner ma recherche, " + criteres.getQuestionDeRelance();
            }
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
                    StatutVerification.VERIFIEE,
                    null
            );

            // Imposer simultanément la disponibilité DISPONIBLE (exclut RESERVEE, LOUEE, VENDUE, SUSPENDUE)
            spec = spec.and((root, query, cb) -> cb.equal(root.get("statutPublication"), StatutPublication.DISPONIBLE));

            // Imposer l'égalité stricte sur l'étage si renseigné
            if (criteres.getEtage() != null) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("etage"), criteres.getEtage()));
            }

            Page<AnnonceEntity> annoncesPage = annonceRepository.findAll(spec, PageRequest.of(0, 5));
            annoncesSuggeres = annoncesPage.getContent().stream()
                    .map(annonceMapper::toResponseDTO)
                    .toList();

            // Tentative de génération conversationnelle via Groq
            if (groqClientService != null && groqClientService.isConfigured()) {
                textReponse = groqClientService.generateConversationalResponse(
                        request.getMessage(),
                        criteres,
                        annoncesSuggeres,
                        null
                );
            }

            // Fallback déterministe si Groq n'a pas répondu ou indisponible
            if (textReponse == null || textReponse.isBlank()) {
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
        }

        // 4. Enregistrer le message de l'assistant
        java.time.LocalDateTime nowAssistant = now();
        MessageIAEntity assistantMessage = MessageIAEntity.builder()
                .conversation(conv)
                .role(RoleMessageIA.ASSISTANT)
                .contenu(textReponse)
                .criteresExtraits(criteresJson)
                .nombreAnnoncesSuggerees(annoncesSuggeres != null ? annoncesSuggeres.size() : 0)
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
        return obtenirHistorique(conversationId, null, (String) null);
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(UUID conversationId, String userEmail) {
        return obtenirHistorique(conversationId, userEmail, (String) null);
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(UUID conversationId, String userEmail, HttpServletRequest httpRequest) {
        return obtenirHistorique(conversationId, userEmail, extractSessionToken(httpRequest));
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(UUID conversationId, String userEmail, String sessionToken) {
        ConversationIAEntity conv = conversationIARepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

        verifierAccesConversation(conv, userEmail, sessionToken);

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

    public void verifierAccesConversation(ConversationIAEntity conv, String userEmail, String sessionToken) {
        if (conv.getUtilisateur() != null) {
            String ownerEmail = conv.getUtilisateur().getEmail();
            if (userEmail == null || userEmail.trim().isEmpty() || "anonymousUser".equals(userEmail)
                    || ownerEmail == null || !ownerEmail.equalsIgnoreCase(userEmail.trim())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Accès refusé : cette conversation appartient à un autre utilisateur"
                );
            }
        } else {
            // Conversation anonyme : preuve de possession obligatoire
            if (conv.getSessionTokenHash() == null || conv.getSessionTokenHash().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Accès refusé : conversation anonyme non vérifiée (absence de preuve de possession)"
                );
            }

            if (conv.getSessionExpiration() != null && conv.getSessionExpiration().isBefore(now())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Accès refusé : session de conversation anonyme expirée"
                );
            }

            if (sessionToken == null || sessionToken.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Accès refusé : preuve de possession requise pour cette conversation anonyme"
                );
            }

            String incomingHash = hashToken(sessionToken);
            if (!MessageDigest.isEqual(incomingHash.getBytes(StandardCharsets.UTF_8),
                                      conv.getSessionTokenHash().getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Accès refusé : preuve de possession invalide pour cette conversation anonyme"
                );
            }
        }
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

    private boolean estQuestionResolue(String question, CriteresExtraitsDTO criteres) {
        if (question == null || question.isBlank() || criteres == null) {
            return true;
        }
        String qLower = question.toLowerCase();
        boolean mentionsBudget = qLower.contains("budget") || qLower.contains("prix");
        boolean mentionsType = qLower.contains("type") || qLower.contains("appartement") || qLower.contains("villa") || qLower.contains("studio") || qLower.contains("chambre");
        boolean mentionsQuartier = qLower.contains("quartier") || qLower.contains("zone") || qLower.contains("secteur") || qLower.contains("localisation");
        boolean mentionsVille = qLower.contains("ville");
        boolean mentionsPieces = qLower.contains("pièce") || qLower.contains("piece") || qLower.contains("nombre de pièces") || qLower.contains("nombre de pieces");
        boolean mentionsEtage = qLower.contains("étage") || qLower.contains("etage");
        boolean mentionsMeuble = qLower.contains("meubl");
        boolean mentionsAnimaux = qLower.contains("anim") || qLower.contains("chat") || qLower.contains("chien");
        boolean mentionsSuperficie = qLower.contains("surface") || qLower.contains("superficie") || qLower.contains("m²") || qLower.contains("m2");

        if (mentionsBudget && (criteres.getBudgetMax() == null && criteres.getBudgetMin() == null)) {
            return false;
        }
        if (mentionsType && criteres.getType() == null) {
            return false;
        }
        if (mentionsQuartier && criteres.getQuartier() == null) {
            return false;
        }
        if (mentionsVille && criteres.getVille() == null) {
            return false;
        }
        if (mentionsPieces && criteres.getNombrePieces() == null) {
            return false;
        }
        if (mentionsEtage && criteres.getEtage() == null) {
            return false;
        }
        if (mentionsMeuble && criteres.getMeuble() == null) {
            return false;
        }
        if (mentionsAnimaux && criteres.getAnimauxAcceptes() == null) {
            return false;
        }
        if (mentionsSuperficie && criteres.getSuperficieMin() == null) {
            return false;
        }
        return true;
    }
}
