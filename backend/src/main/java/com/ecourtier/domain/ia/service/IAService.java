package com.ecourtier.domain.ia.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
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
import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IAService {

    public static final String IA_SESSION_COOKIE_NAME = "ecourtier_ia_session";
    public static final String IA_SESSION_HEADER_NAME = "X-IA-Session-Token";
    public static final int IA_SESSION_DURATION_DAYS = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");

    private final ConversationIARepository conversationIARepository;
    private final MessageIARepository messageIARepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AnnonceRepository annonceRepository;
    private final AnnonceMapper annonceMapper;
    private final LlmExtractionService llmExtractionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate writeTransactionTemplate;
    private Clock clock = Clock.system(DAKAR_ZONE);

    @Autowired
    public IAService(
            ConversationIARepository conversationIARepository,
            MessageIARepository messageIARepository,
            UtilisateurRepository utilisateurRepository,
            AnnonceRepository annonceRepository,
            AnnonceMapper annonceMapper,
            LlmExtractionService llmExtractionService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.conversationIARepository = conversationIARepository;
        this.messageIARepository = messageIARepository;
        this.utilisateurRepository = utilisateurRepository;
        this.annonceRepository = annonceRepository;
        this.annonceMapper = annonceMapper;
        this.llmExtractionService = llmExtractionService;
        this.objectMapper = objectMapper;
        if (transactionManager != null) {
            this.readTransactionTemplate = new TransactionTemplate(transactionManager);
            this.readTransactionTemplate.setReadOnly(true);
            this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        } else {
            this.readTransactionTemplate = null;
            this.writeTransactionTemplate = null;
        }
    }

    public IAService(
            ConversationIARepository conversationIARepository,
            MessageIARepository messageIARepository,
            UtilisateurRepository utilisateurRepository,
            AnnonceRepository annonceRepository,
            AnnonceMapper annonceMapper,
            LlmExtractionService llmExtractionService,
            ObjectMapper objectMapper
    ) {
        this(conversationIARepository, messageIARepository, utilisateurRepository,
                annonceRepository, annonceMapper, llmExtractionService, objectMapper, null);
    }

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(DAKAR_ZONE);
    }

    private LocalDateTime nowDakar() {
        return LocalDateTime.now((clock != null ? clock : Clock.system(DAKAR_ZONE)).withZone(DAKAR_ZONE));
    }

    private <T> T executeInReadTx(Supplier<T> action) {
        if (readTransactionTemplate != null) {
            return readTransactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private <T> T executeInWriteTx(Supplier<T> action) {
        if (writeTransactionTemplate != null) {
            return writeTransactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    public static String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
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

    private ConversationCreateResponseDTO creerConversationInternal(
            String userEmail,
            String sessionToken,
            HttpServletResponse response,
            boolean isSecure
    ) {
        UtilisateurEntity user = null;
        boolean isAuthentifie = (userEmail != null && !userEmail.trim().isEmpty() && !"anonymousUser".equals(userEmail));
        if (isAuthentifie) {
            user = utilisateurRepository.findByEmail(userEmail).orElse(null);
        }

        String sessionTokenHash = null;
        LocalDateTime expiration = null;
        LocalDateTime now = nowDakar();

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
            expiration = now.plusDays(IA_SESSION_DURATION_DAYS);
        }

        ConversationIAEntity conv = ConversationIAEntity.builder()
                .utilisateur(user)
                .sessionTokenHash(sessionTokenHash)
                .sessionExpiration(expiration)
                .dateCreation(now)
                .build();

        ConversationIAEntity saved = conversationIARepository.save(conv);
        return ConversationCreateResponseDTO.builder()
                .conversationId(saved.getId())
                .dateCreation(saved.getDateCreation() != null ? saved.getDateCreation() : conv.getDateCreation())
                .build();
    }

    public MessageIaResponseDTO envoyerMessage(UUID conversationId, MessageUserRequestDTO request, String emailUtilisateur) {
        return envoyerMessage(conversationId, request, emailUtilisateur, (String) null);
    }

    public MessageIaResponseDTO envoyerMessage(
            UUID conversationId,
            MessageUserRequestDTO request,
            String emailUtilisateur,
            HttpServletRequest httpRequest
    ) {
        return envoyerMessage(conversationId, request, emailUtilisateur, extractSessionToken(httpRequest));
    }

    /**
     * Traite un message utilisateur sans maintenir de transaction SQL ouverte pendant les appels HTTP externes aux LLM (ANO-IA-02).
     * Étape 1 : Lecture courte en base (contrôle d'accès + contexte précédent).
     * Étape 2 : Appel LLM / extraction hors transaction SQL + validation.
     * Étape 3 : Recherche en lecture seule des annonces réelles et vérifiées + scoring et explications.
     * Étape 4 : Génération de la réponse conversationnelle hors transaction SQL.
     * Étape 5 : Transaction courte d'écriture pour persister les messages utilisateur et assistant.
     * Étape 6 : Construction de la réponse avec métriques de temps.
     */
    public MessageIaResponseDTO envoyerMessage(
            UUID conversationId,
            MessageUserRequestDTO request,
            String emailUtilisateur,
            String rawSessionToken
    ) {
        long tTotalStart = System.nanoTime();

        // =========================================================================
        // ÉTAPE 1 : Transaction courte de lecture du contexte et contrôle d'accès
        // =========================================================================
        CriteresExtraitsDTO criteresPrecedents = executeInReadTx(() -> {
            ConversationIAEntity conversation = conversationIARepository.findById(conversationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));
            verifierDroitsConversation(conversation, emailUtilisateur, rawSessionToken);

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

            List<MessageIAEntity> historique = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conversationId);
            return extraireDerniersCriteresConnus(historique);
        });

        // =========================================================================
        // ÉTAPE 2 : Extraction NLU & appel fournisseur LLM HORS transaction SQL
        // =========================================================================
        long tExtractStart = System.nanoTime();
        CriteresExtraitsDTO nouveauxCriteres = llmExtractionService.extractCriteria(request.getMessage(), criteresPrecedents);
        CriteresExtraitsDTO criteresFinaux = fusionnerCriteres(criteresPrecedents, nouveauxCriteres);
        long tempsExtractionMs = (System.nanoTime() - tExtractStart) / 1_000_000L;

        boolean horsSujet = Boolean.TRUE.equals(nouveauxCriteres.getHorsSujet());
        String intentionSpeciale = nouveauxCriteres.getIntentionSpeciale();

        boolean aucunCritere = criteresFinaux.getType() == null
                && criteresFinaux.getModeTransaction() == null
                && criteresFinaux.getVille() == null
                && criteresFinaux.getQuartier() == null
                && (criteresFinaux.getQuartiers() == null || criteresFinaux.getQuartiers().isEmpty())
                && criteresFinaux.getBudgetMax() == null
                && criteresFinaux.getBudgetMin() == null
                && criteresFinaux.getNombrePieces() == null
                && criteresFinaux.getNombreChambres() == null
                && criteresFinaux.getMeuble() == null
                && criteresFinaux.getSuperficieMin() == null
                && criteresFinaux.getSuperficieMax() == null
                && criteresFinaux.getEtage() == null
                && criteresFinaux.getAscenseur() == null
                && criteresFinaux.getAnimauxAcceptes() == null
                && (criteresFinaux.getEquipements() == null || criteresFinaux.getEquipements().isEmpty())
                && (criteresFinaux.getProximites() == null || criteresFinaux.getProximites().isEmpty())
                && !"LISTE_DISPONIBLE".equals(intentionSpeciale);

        // =========================================================================
        // ÉTAPE 3 : Recherche en lecture seule des annonces réelles et vérifiées
        // =========================================================================
        long tSearchStart = System.nanoTime();
        SearchExecutionResult searchResult;
        if (horsSujet || "FAQ_DOCUMENTS".equals(intentionSpeciale) || (aucunCritere && !"EXPLICATION_RECOMMANDATION".equals(intentionSpeciale))) {
            searchResult = new SearchExecutionResult(List.of(), Map.of(), null);
        } else if ("EXPLICATION_RECOMMANDATION".equals(intentionSpeciale)
                && criteresPrecedents != null
                && criteresPrecedents.getAnnoncesSuggereesIds() != null
                && !criteresPrecedents.getAnnoncesSuggereesIds().isEmpty()) {
            searchResult = executeInReadTx(() -> chargerAnnoncesParIdsAvecExplications(criteresPrecedents.getAnnoncesSuggereesIds(), criteresFinaux));
        } else {
            searchResult = executeInReadTx(() -> rechercherEtClasserAnnoncesPertinentes(criteresFinaux));
        }
        long tempsRechercheMs = (System.nanoTime() - tSearchStart) / 1_000_000L;

        List<AnnonceResponseDTO> annoncesSuggerees = searchResult.annonces();
        Map<String, String> explicationsAnnonces = searchResult.explications();
        String diagnosticCriteresBloquants = searchResult.diagnosticBlocage();

        if (!annoncesSuggerees.isEmpty()) {
            criteresFinaux.setAnnoncesSuggereesIds(
                    annoncesSuggerees.stream().map(AnnonceResponseDTO::getId).filter(Objects::nonNull).collect(Collectors.toList())
            );
            criteresFinaux.setExplicationsAnnonces(explicationsAnnonces);
        }

        // =========================================================================
        // ÉTAPE 4 : Génération de la réponse conversationnelle HORS transaction SQL
        // =========================================================================
        long tGenStart = System.nanoTime();
        String texteReponse;
        if (horsSujet) {
            texteReponse = criteresFinaux.getQuestionDeRelance() != null
                    ? criteresFinaux.getQuestionDeRelance()
                    : "Je suis Samba, l'assistant immobilier d'E-Courtier Sénégal. Je vous accompagne exclusivement dans la recherche de logements (chambres, studios, appartements, villas). Quel bien immobilier recherchez-vous ?";
        } else if ("FAQ_DOCUMENTS".equals(intentionSpeciale)) {
            texteReponse = construireReponseFaqDocuments(criteresFinaux);
        } else if ("EXPLICATION_RECOMMANDATION".equals(intentionSpeciale)) {
            texteReponse = construireReponseExplicationRecommandation(annoncesSuggerees, explicationsAnnonces, criteresFinaux);
        } else if (aucunCritere) {
            texteReponse = construireReponseAssistant(criteresFinaux, annoncesSuggerees, explicationsAnnonces, diagnosticCriteresBloquants);
        } else if (Boolean.TRUE.equals(nouveauxCriteres.getModeSecours()) || !llmExtractionService.isExternalGeminiActive()) {
            criteresFinaux.setModeSecours(true);
            criteresFinaux.setFournisseurIa("fallback-local");
            texteReponse = construireReponseAssistant(criteresFinaux, annoncesSuggerees, explicationsAnnonces, diagnosticCriteresBloquants);
        } else {
            String llmResponse = llmExtractionService.generateConversationalResponse(
                    request.getMessage(),
                    criteresFinaux,
                    annoncesSuggerees,
                    criteresFinaux.getQuestionDeRelance()
            );
            if (llmResponse != null && !llmResponse.isBlank()) {
                texteReponse = llmResponse;
                if (annoncesSuggerees.isEmpty() && diagnosticCriteresBloquants != null && !texteReponse.contains(diagnosticCriteresBloquants)) {
                    texteReponse = texteReponse + " " + diagnosticCriteresBloquants;
                }
            } else {
                criteresFinaux.setModeSecours(true);
                criteresFinaux.setFournisseurIa("fallback-local");
                texteReponse = construireReponseAssistant(criteresFinaux, annoncesSuggerees, explicationsAnnonces, diagnosticCriteresBloquants);
            }
        }
        final String texteReponseFinal = texteReponse;
        long tempsGenerationMs = (System.nanoTime() - tGenStart) / 1_000_000L;

        // =========================================================================
        // ÉTAPE 5 : Transaction courte d'écriture pour persister l'échange
        // =========================================================================
        long tPersistStart = System.nanoTime();
        String criteresJson = serializeCriteres(criteresFinaux);
        LocalDateTime now = nowDakar();

        MessageIAEntity savedAssistantMsg = executeInWriteTx(() -> {
            ConversationIAEntity conversation = conversationIARepository.findById(conversationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

            boolean isAuth = emailUtilisateur != null && !emailUtilisateur.trim().isEmpty() && !"anonymousUser".equals(emailUtilisateur);
            if (conversation.getUtilisateur() == null && isAuth) {
                utilisateurRepository.findByEmail(emailUtilisateur).ifPresent(conversation::setUtilisateur);
            }

            MessageIAEntity userMessageEntity = MessageIAEntity.builder()
                    .conversation(conversation)
                    .role(RoleMessageIA.UTILISATEUR)
                    .contenu(request.getMessage().trim())
                    .dateEnvoi(now)
                    .build();
            messageIARepository.save(userMessageEntity);

            MessageIAEntity assistantMessageEntity = MessageIAEntity.builder()
                    .conversation(conversation)
                    .role(RoleMessageIA.ASSISTANT)
                    .contenu(texteReponseFinal)
                    .criteresExtraits(criteresJson)
                    .nombreAnnoncesSuggerees(annoncesSuggerees.size())
                    .dateEnvoi(now)
                    .build();
            return messageIARepository.save(assistantMessageEntity);
        });
        long tempsPersistanceMs = (System.nanoTime() - tPersistStart) / 1_000_000L;

        // =========================================================================
        // ÉTAPE 6 : Construction de la réponse et journalisation des métriques
        // =========================================================================
        long tempsTotalMs = (System.nanoTime() - tTotalStart) / 1_000_000L;
        long tempsLlmMs = tempsExtractionMs + tempsGenerationMs;

        log.info("IA_MESSAGE_PROCESSED convId={} provider={} fallback={} totalMs={} llmMs={} extractMs={} searchMs={} persistMs={} results={}",
                conversationId,
                criteresFinaux.getFournisseurIa(),
                criteresFinaux.getModeSecours(),
                tempsTotalMs,
                tempsLlmMs,
                tempsExtractionMs,
                tempsRechercheMs,
                tempsPersistanceMs,
                annoncesSuggerees.size());

        return MessageIaResponseDTO.builder()
                .id(savedAssistantMsg.getId())
                .reponse(texteReponse)
                .annoncesSuggeres(annoncesSuggerees)
                .criteresExtraits(criteresFinaux)
                .questionDeRelance(criteresFinaux.getQuestionDeRelance())
                .dateEnvoi(savedAssistantMsg.getDateEnvoi())
                .build();
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(UUID conversationId, String emailUtilisateur) {
        return obtenirHistorique(conversationId, emailUtilisateur, (String) null);
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(
            UUID conversationId,
            String emailUtilisateur,
            HttpServletRequest httpRequest
    ) {
        return obtenirHistorique(conversationId, emailUtilisateur, extractSessionToken(httpRequest));
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponseDTO obtenirHistorique(UUID conversationId, String emailUtilisateur, String rawSessionToken) {
        ConversationIAEntity conversation = conversationIARepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

        verifierDroitsConversation(conversation, emailUtilisateur, rawSessionToken);

        List<MessageIAEntity> messages = messageIARepository.findByConversationIdOrderByDateEnvoiAsc(conversationId);

        List<MessageItemDTO> messageItems = messages.stream().map(m -> {
            CriteresExtraitsDTO criteres = null;
            List<AnnonceResponseDTO> restoredAnnonces = null;
            String questionRelance = null;

            if (m.getCriteresExtraits() != null && !m.getCriteresExtraits().isBlank()) {
                try {
                    criteres = objectMapper.readValue(m.getCriteresExtraits(), CriteresExtraitsDTO.class);
                    questionRelance = criteres.getQuestionDeRelance();
                    if (criteres.getAnnoncesSuggereesIds() != null && !criteres.getAnnoncesSuggereesIds().isEmpty()) {
                        restoredAnnonces = restaurerAnnoncesSuggereesDisponibles(criteres.getAnnoncesSuggereesIds());
                    }
                } catch (Exception e) {
                    log.warn("Erreur de lecture des critères JSON de l'historique message {}", m.getId());
                }
            }
            return MessageItemDTO.builder()
                    .id(m.getId())
                    .role(m.getRole())
                    .contenu(m.getContenu())
                    .criteresExtraits(criteres)
                    .annoncesSuggeres(restoredAnnonces)
                    .questionDeRelance(questionRelance)
                    .dateEnvoi(m.getDateEnvoi())
                    .build();
        }).collect(Collectors.toList());

        return ConversationHistoryResponseDTO.builder()
                .conversationId(conversation.getId())
                .dateCreation(conversation.getDateCreation())
                .messages(messageItems)
                .build();
    }

    private List<AnnonceResponseDTO> restaurerAnnoncesSuggereesDisponibles(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnnonceEntity> byId = annonceRepository.findAllById(ids).stream()
                .filter(a -> !a.isSupprime())
                .filter(a -> a.getStatutPublication() == StatutPublication.DISPONIBLE)
                .filter(a -> a.getStatutVerification() == StatutVerification.VERIFIEE)
                .collect(Collectors.toMap(AnnonceEntity::getId, a -> a, (a, b) -> a));

        List<AnnonceResponseDTO> ordered = new ArrayList<>();
        for (UUID id : ids) {
            AnnonceEntity entity = byId.get(id);
            if (entity != null) {
                ordered.add(sanitizeAnnonceForAssistant(annonceMapper.toResponseDTO(entity)));
            }
        }
        return ordered;
    }

    /**
     * Masque les données personnelles privées (email et téléphone de l'auteur) ainsi que les documents
     * de vérification avant tout envoi dans les réponses de l'assistant IA (Correction ANO-IA-01).
     */
    public AnnonceResponseDTO sanitizeAnnonceForAssistant(AnnonceResponseDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getAuteur() != null) {
            UtilisateurResponseDTO src = dto.getAuteur();
            dto.setAuteur(UtilisateurResponseDTO.builder()
                    .id(src.getId())
                    .nom(src.getNom())
                    .prenom(src.getPrenom())
                    .role(src.getRole())
                    .dateCreation(src.getDateCreation())
                    .actif(src.isActif())
                    .photoProfilUrl(src.getPhotoProfilUrl())
                    .email(null)
                    .telephone(null)
                    .build());
        }
        dto.setDocumentsVerification(null);
        return dto;
    }

    private void verifierDroitsConversation(ConversationIAEntity conversation, String emailUtilisateur, String rawSessionToken) {
        if (conversation.getUtilisateur() != null) {
            String ownerEmail = conversation.getUtilisateur().getEmail();
            if (emailUtilisateur == null || emailUtilisateur.trim().isEmpty() || "anonymousUser".equals(emailUtilisateur)
                    || ownerEmail == null || !ownerEmail.equalsIgnoreCase(emailUtilisateur.trim())) {
                log.warn("Tentative d'accès non autorisé à la conversation {} par '{}'", conversation.getId(), emailUtilisateur);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : cette conversation appartient à un autre utilisateur");
            }
            return;
        }

        String expectedHash = conversation.getSessionTokenHash();
        if (expectedHash == null || expectedHash.isBlank()) {
            log.warn("Accès refusé à la conversation anonyme {} dépourvue de preuve de possession", conversation.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : conversation anonyme non vérifiée (absence de preuve de possession)");
        }

        if (conversation.getSessionExpiration() != null && conversation.getSessionExpiration().isBefore(nowDakar())) {
            log.warn("Accès refusé à la conversation anonyme {} : session expirée", conversation.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : session de conversation anonyme expirée");
        }

        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            log.warn("Tentative d'accès sans jeton de session à la conversation anonyme {}", conversation.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : preuve de possession requise pour cette conversation anonyme");
        }

        String providedHash = hashToken(rawSessionToken);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                providedHash.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Tentative d'accès avec un jeton de session invalide à la conversation anonyme {}", conversation.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : preuve de possession invalide pour cette conversation anonyme");
        }
    }

    private CriteresExtraitsDTO extraireDerniersCriteresConnus(List<MessageIAEntity> historique) {
        if (historique == null || historique.isEmpty()) {
            return null;
        }
        for (int i = historique.size() - 1; i >= 0; i--) {
            MessageIAEntity msg = historique.get(i);
            if (msg.getCriteresExtraits() != null && !msg.getCriteresExtraits().isBlank()) {
                try {
                    return objectMapper.readValue(msg.getCriteresExtraits(), CriteresExtraitsDTO.class);
                } catch (Exception e) {
                    log.warn("Impossible de désérialiser les critères précédents du message {}", msg.getId());
                }
            }
        }
        return null;
    }

    private CriteresExtraitsDTO fusionnerCriteres(CriteresExtraitsDTO precedents, CriteresExtraitsDTO nouveaux) {
        if (precedents == null) {
            return nouveaux;
        }
        if (nouveaux == null) {
            return precedents;
        }

        Set<String> removals = nouveaux.getCriteresASupprimer() != null ? nouveaux.getCriteresASupprimer() : Set.of();

        String mergedQuartier;
        List<String> mergedQuartiers;
        if (removals.contains("quartier")) {
            mergedQuartier = null;
            mergedQuartiers = null;
        } else if (nouveaux.getQuartiers() != null && !nouveaux.getQuartiers().isEmpty()) {
            mergedQuartiers = nouveaux.getQuartiers();
            mergedQuartier = nouveaux.getQuartier() != null ? nouveaux.getQuartier() : mergedQuartiers.get(0);
        } else if (nouveaux.getQuartier() != null) {
            mergedQuartier = nouveaux.getQuartier();
            mergedQuartiers = List.of(nouveaux.getQuartier());
        } else {
            mergedQuartier = precedents.getQuartier();
            mergedQuartiers = precedents.getQuartiers();
        }

        Set<Equipement> mergedEquipements = null;
        if ((precedents.getEquipements() != null && !precedents.getEquipements().isEmpty())
                || (nouveaux.getEquipements() != null && !nouveaux.getEquipements().isEmpty())) {
            mergedEquipements = new LinkedHashSet<>();
            if (precedents.getEquipements() != null) mergedEquipements.addAll(precedents.getEquipements());
            if (nouveaux.getEquipements() != null) mergedEquipements.addAll(nouveaux.getEquipements());
        }

        Set<ProximiteTag> mergedProximites = null;
        if ((precedents.getProximites() != null && !precedents.getProximites().isEmpty())
                || (nouveaux.getProximites() != null && !nouveaux.getProximites().isEmpty())) {
            mergedProximites = new LinkedHashSet<>();
            if (precedents.getProximites() != null) mergedProximites.addAll(precedents.getProximites());
            if (nouveaux.getProximites() != null) mergedProximites.addAll(nouveaux.getProximites());
        }

        CriteresExtraitsDTO merged = CriteresExtraitsDTO.builder()
                .type(nouveaux.getType() != null ? nouveaux.getType() : precedents.getType())
                .modeTransaction(nouveaux.getModeTransaction() != null ? nouveaux.getModeTransaction() : precedents.getModeTransaction())
                .ville(nouveaux.getVille() != null ? nouveaux.getVille() : precedents.getVille())
                .quartier(mergedQuartier)
                .quartiers(mergedQuartiers)
                .budgetMin(removals.contains("budget") ? null : (nouveaux.getBudgetMin() != null ? nouveaux.getBudgetMin() : precedents.getBudgetMin()))
                .budgetMax(removals.contains("budget") ? null : (nouveaux.getBudgetMax() != null ? nouveaux.getBudgetMax() : precedents.getBudgetMax()))
                .nombrePieces(nouveaux.getNombrePieces() != null ? nouveaux.getNombrePieces() : precedents.getNombrePieces())
                .nombreChambres(nouveaux.getNombreChambres() != null ? nouveaux.getNombreChambres() : precedents.getNombreChambres())
                .etage(removals.contains("etage") ? null : (nouveaux.getEtage() != null ? nouveaux.getEtage() : precedents.getEtage()))
                .ascenseur(nouveaux.getAscenseur() != null ? nouveaux.getAscenseur() : precedents.getAscenseur())
                .meuble(removals.contains("meuble") ? null : (nouveaux.getMeuble() != null ? nouveaux.getMeuble() : precedents.getMeuble()))
                .animauxAcceptes(nouveaux.getAnimauxAcceptes() != null ? nouveaux.getAnimauxAcceptes() : precedents.getAnimauxAcceptes())
                .superficieMin(removals.contains("superficie") ? null : (nouveaux.getSuperficieMin() != null ? nouveaux.getSuperficieMin() : precedents.getSuperficieMin()))
                .superficieMax(removals.contains("superficie") ? null : (nouveaux.getSuperficieMax() != null ? nouveaux.getSuperficieMax() : precedents.getSuperficieMax()))
                .equipements(mergedEquipements)
                .proximites(mergedProximites)
                .confiance(nouveaux.getConfiance() != null ? nouveaux.getConfiance() : precedents.getConfiance())
                .fournisseurIa(nouveaux.getFournisseurIa())
                .modeSecours(nouveaux.getModeSecours())
                .horsSujet(nouveaux.getHorsSujet())
                .intentionSpeciale(nouveaux.getIntentionSpeciale())
                .build();

        String questionRelance = resoudreQuestionDeRelanceMultiTours(precedents, nouveaux, merged);
        merged.setQuestionDeRelance(questionRelance);

        return merged;
    }

    private String resoudreQuestionDeRelanceMultiTours(
            CriteresExtraitsDTO precedents,
            CriteresExtraitsDTO nouveaux,
            CriteresExtraitsDTO merged
    ) {
        if (Boolean.TRUE.equals(nouveaux.getHorsSujet())) {
            return nouveaux.getQuestionDeRelance();
        }
        if (nouveaux.getQuestionDeRelance() != null
                && nouveaux.getQuestionDeRelance().contains("E-Courtier propose exclusivement des logements d'habitation")) {
            return nouveaux.getQuestionDeRelance();
        }
        if (nouveaux.getIntentionSpeciale() != null) {
            return null;
        }

        String prevQ = precedents != null ? precedents.getQuestionDeRelance() : null;
        if (prevQ != null && !prevQ.isBlank()) {
            String prevLower = prevQ.toLowerCase(Locale.ROOT);
            boolean askedOnlyQuartier = prevLower.contains("quartier") && !prevLower.contains("ville") && !prevLower.contains("budget");
            if (askedOnlyQuartier && merged.getQuartier() == null) {
                return prevQ;
            }
        }

        boolean hasBudget = merged.getBudgetMax() != null || merged.getBudgetMin() != null;
        boolean hasType = merged.getType() != null;
        boolean hasLocalisation = merged.getVille() != null || merged.getQuartier() != null
                || (merged.getQuartiers() != null && !merged.getQuartiers().isEmpty());

        if (!hasBudget && !hasType) {
            return "Pour affiner ma recherche, quel est votre budget maximum (en FCFA) et quel type de bien (studio, appartement, villa, chambre) recherchez-vous ?";
        }
        if (!hasBudget) {
            return "Quel est votre budget maximum en FCFA pour ce bien ?";
        }
        if (!hasLocalisation && !hasType) {
            return "Dans quelle ville ou quel quartier recherchez-vous et quel type de logement souhaitez-vous ?";
        }
        return null;
    }

    private record SearchExecutionResult(
            List<AnnonceResponseDTO> annonces,
            Map<String, String> explications,
            String diagnosticBlocage
    ) {}

    private SearchExecutionResult chargerAnnoncesParIdsAvecExplications(List<UUID> ids, CriteresExtraitsDTO criteres) {
        List<AnnonceResponseDTO> restored = restaurerAnnoncesSuggereesDisponibles(ids);
        Map<String, String> explications = new LinkedHashMap<>();
        for (AnnonceResponseDTO dto : restored) {
            explications.put(dto.getId().toString(), construireJustificationAnnonce(dto, criteres));
        }
        return new SearchExecutionResult(restored, explications, null);
    }

    private SearchExecutionResult rechercherEtClasserAnnoncesPertinentes(CriteresExtraitsDTO criteres) {
        List<AnnonceEntity> poolVerifieDisponible = annonceRepository.findAll().stream()
                .filter(a -> !a.isSupprime())
                .filter(a -> a.getStatutPublication() == StatutPublication.DISPONIBLE)
                .filter(a -> a.getStatutVerification() == StatutVerification.VERIFIEE)
                .toList();

        List<AnnonceEntity> exactMatches = poolVerifieDisponible.stream()
                .filter(a -> matchesAllActiveCriteria(a, criteres))
                .sorted(Comparator
                        .comparingInt((AnnonceEntity a) -> calculerScorePertinence(a, criteres)).reversed()
                        .thenComparing(a -> a.getPrix() != null ? a.getPrix() : java.math.BigDecimal.ZERO))
                .limit(5)
                .toList();

        if (!exactMatches.isEmpty()) {
            List<AnnonceResponseDTO> dtos = new ArrayList<>();
            Map<String, String> explications = new LinkedHashMap<>();
            for (AnnonceEntity entity : exactMatches) {
                AnnonceResponseDTO sanitized = sanitizeAnnonceForAssistant(annonceMapper.toResponseDTO(entity));
                dtos.add(sanitized);
                explications.put(sanitized.getId().toString(), construireJustificationAnnonce(sanitized, criteres));
            }
            return new SearchExecutionResult(dtos, explications, null);
        }

        String diagnostic = diagnostiquerCriteresBloquants(poolVerifieDisponible, criteres);
        return new SearchExecutionResult(List.of(), Map.of(), diagnostic);
    }

    private boolean matchesAllActiveCriteria(AnnonceEntity a, CriteresExtraitsDTO criteres) {
        if (criteres.getType() != null && a.getType() != criteres.getType()) {
            return false;
        }
        if (criteres.getModeTransaction() != null && a.getModeTransaction() != criteres.getModeTransaction()) {
            return false;
        }
        if (criteres.getVille() != null && (a.getVille() == null || !normalizeText(a.getVille()).contains(normalizeText(criteres.getVille())))) {
            return false;
        }
        if (criteres.getQuartiers() != null && !criteres.getQuartiers().isEmpty()) {
            boolean matchesAnyQuartier = criteres.getQuartiers().stream()
                    .anyMatch(q -> matchesQuartier(a, q));
            if (!matchesAnyQuartier) {
                return false;
            }
        } else if (criteres.getQuartier() != null) {
            if (!matchesQuartier(a, criteres.getQuartier())) {
                return false;
            }
        }
        if (criteres.getBudgetMax() != null && (a.getPrix() == null || a.getPrix().compareTo(criteres.getBudgetMax()) > 0)) {
            return false;
        }
        if (criteres.getBudgetMin() != null && (a.getPrix() == null || a.getPrix().compareTo(criteres.getBudgetMin()) < 0)) {
            return false;
        }
        if (criteres.getNombrePieces() != null && (a.getNombrePieces() == null || a.getNombrePieces() < criteres.getNombrePieces())) {
            return false;
        }
        if (criteres.getNombreChambres() != null && criteres.getNombrePieces() == null) {
            if (a.getNombrePieces() != null && a.getNombrePieces() < criteres.getNombreChambres()) {
                return false;
            }
        }
        if (criteres.getSuperficieMin() != null && (a.getSuperficie() == null || a.getSuperficie() < criteres.getSuperficieMin())) {
            return false;
        }
        if (criteres.getSuperficieMax() != null && (a.getSuperficie() == null || a.getSuperficie() > criteres.getSuperficieMax())) {
            return false;
        }
        if (criteres.getEtage() != null && (a.getEtage() == null || !a.getEtage().equals(criteres.getEtage()))) {
            return false;
        }
        if (criteres.getMeuble() != null && a.isMeuble() != criteres.getMeuble()) {
            return false;
        }
        if (criteres.getAnimauxAcceptes() != null && a.isAnimauxAcceptes() != criteres.getAnimauxAcceptes()) {
            return false;
        }
        return true;
    }

    private boolean matchesQuartier(AnnonceEntity a, String requestedQuartier) {
        if (requestedQuartier == null || requestedQuartier.isBlank()) return true;
        String qNorm = normalizeText(requestedQuartier);
        return (a.getQuartier() != null && normalizeText(a.getQuartier()).contains(qNorm))
                || (a.getTitre() != null && normalizeText(a.getTitre()).contains(qNorm))
                || (a.getDescription() != null && normalizeText(a.getDescription()).contains(qNorm));
    }

    private int calculerScorePertinence(AnnonceEntity a, CriteresExtraitsDTO c) {
        int score = 0;
        if (c.getType() != null && a.getType() == c.getType()) {
            score += 20;
        }
        if (c.getModeTransaction() != null && a.getModeTransaction() == c.getModeTransaction()) {
            score += 15;
        }
        if (c.getVille() != null && a.getVille() != null && normalizeText(a.getVille()).equals(normalizeText(c.getVille()))) {
            score += 20;
        }
        if (c.getQuartier() != null && a.getQuartier() != null && normalizeText(a.getQuartier()).contains(normalizeText(c.getQuartier()))) {
            score += 30;
        }
        if (c.getBudgetMax() != null && a.getPrix() != null && c.getBudgetMax().compareTo(java.math.BigDecimal.ZERO) > 0) {
            double ratio = a.getPrix().doubleValue() / c.getBudgetMax().doubleValue();
            if (ratio <= 0.85) {
                score += 15;
            } else if (ratio <= 1.0) {
                score += 10;
            }
        }
        if (c.getNombrePieces() != null && a.getNombrePieces() != null) {
            if (a.getNombrePieces().equals(c.getNombrePieces())) {
                score += 12;
            } else if (a.getNombrePieces() > c.getNombrePieces()) {
                score += 6;
            }
        }
        if (c.getEquipements() != null && !c.getEquipements().isEmpty() && a.getEquipements() != null) {
            for (Equipement eq : c.getEquipements()) {
                if (a.getEquipements().contains(eq)) {
                    score += 5;
                }
            }
        }
        if (c.getProximites() != null && !c.getProximites().isEmpty() && a.getProximites() != null) {
            for (ProximiteTag p : c.getProximites()) {
                if (a.getProximites().contains(p)) {
                    score += 5;
                }
            }
        }
        if (a.getPhotos() != null && !a.getPhotos().isEmpty()) {
            score += 5;
        }
        return score;
    }

    private String construireJustificationAnnonce(AnnonceResponseDTO a, CriteresExtraitsDTO c) {
        List<String> points = new ArrayList<>();
        if (a.getType() != null) {
            points.add(a.getType().name().toLowerCase(Locale.ROOT) + " vérifié");
        }
        if (a.getQuartier() != null && a.getVille() != null) {
            points.add("situé à " + a.getQuartier() + " (" + a.getVille() + ")");
        }
        if (a.getPrix() != null) {
            if (c != null && c.getBudgetMax() != null) {
                points.add("prix de " + a.getPrix().toPlainString() + " FCFA respectant votre budget de " + c.getBudgetMax().toPlainString() + " FCFA");
            } else {
                points.add("prix de " + a.getPrix().toPlainString() + " FCFA");
            }
        }
        if (a.getNombrePieces() != null) {
            points.add(a.getNombrePieces() + " pièce(s)");
        }
        if (a.getSuperficie() != null) {
            points.add(a.getSuperficie().intValue() + " m²");
        }
        if (a.isMeuble()) {
            points.add("meublé");
        }
        return String.join(", ", points);
    }

    private String diagnostiquerCriteresBloquants(List<AnnonceEntity> pool, CriteresExtraitsDTO c) {
        if (pool == null || pool.isEmpty()) {
            return "Aucune annonce vérifiée et disponible n'est actuellement publiée dans le catalogue.";
        }
        List<String> blocages = new ArrayList<>();
        if (c.getBudgetMax() != null) {
            long withoutBudget = pool.stream().filter(a -> {
                CriteresExtraitsDTO copy = CriteresExtraitsDTO.builder()
                        .type(c.getType())
                        .modeTransaction(c.getModeTransaction())
                        .ville(c.getVille())
                        .quartier(c.getQuartier())
                        .quartiers(c.getQuartiers())
                        .build();
                return matchesAllActiveCriteria(a, copy);
            }).count();
            if (withoutBudget > 0) {
                AnnonceEntity cheapest = pool.stream()
                        .filter(a -> a.getPrix() != null)
                        .min(Comparator.comparing(AnnonceEntity::getPrix))
                        .orElse(null);
                if (cheapest != null) {
                    blocages.add("le budget maximum de " + c.getBudgetMax().toPlainString()
                            + " FCFA est inférieur aux tarifs des biens vérifiés de ce secteur (à partir de "
                            + cheapest.getPrix().toPlainString() + " FCFA pour \"" + cheapest.getTitre() + "\")");
                }
            }
        }
        if (c.getQuartier() != null && blocages.isEmpty()) {
            long inCityWithoutQuartier = pool.stream().filter(a ->
                    c.getVille() == null || (a.getVille() != null && normalizeText(a.getVille()).contains(normalizeText(c.getVille())))
            ).count();
            if (inCityWithoutQuartier > 0) {
                blocages.add("aucun bien vérifié n'est disponible dans le quartier " + c.getQuartier()
                        + ", mais " + inCityWithoutQuartier + " bien(s) vérifié(s) sont disponibles dans d'autres quartiers");
            }
        }
        if (blocages.isEmpty()) {
            return null;
        }
        return "Diagnostic : " + String.join(" ; ", blocages) + ".";
    }

    private String construireReponseFaqDocuments(CriteresExtraitsDTO criteres) {
        return "Pour louer un logement sur E-Courtier Sénégal, préparez les documents suivants : "
                + "1) Une pièce d'identité valide (CNI CEDEAO ou passeport), "
                + "2) Vos justificatifs de revenus ou d'activité (contrat de travail, 3 derniers bulletins de salaire, attestation de bourse ou registre de commerce), "
                + "3) Le dépôt de garantie (caution réglementaire au Sénégal) et le premier mois de loyer payables uniquement après la visite confirmée et la génération électronique du contrat de bail sur la plateforme. "
                + "Souhaitez-vous poursuivre votre recherche de logement ?";
    }

    private String construireReponseExplicationRecommandation(
            List<AnnonceResponseDTO> annonces,
            Map<String, String> explications,
            CriteresExtraitsDTO criteres
    ) {
        if (annonces == null || annonces.isEmpty()) {
            return "Je n'ai pas encore d'annonce sélectionnée dans notre échange actuel. Indiquez-moi votre ville, votre type de logement et votre budget en FCFA pour que je vous présente une sélection justifiée.";
        }
        StringBuilder sb = new StringBuilder("Voici pourquoi j'ai sélectionné ");
        if (annonces.size() == 1) {
            AnnonceResponseDTO a = annonces.get(0);
            String exp = explications.getOrDefault(a.getId().toString(), construireJustificationAnnonce(a, criteres));
            sb.append("l'annonce \"").append(a.getTitre()).append("\" : ").append(exp)
              .append(". De plus, ses justificatifs ont été vérifiés par l'administration E-Courtier.");
        } else {
            sb.append("ces ").append(annonces.size()).append(" annonces vérifiées : ");
            int idx = 1;
            for (AnnonceResponseDTO a : annonces) {
                String exp = explications.getOrDefault(a.getId().toString(), construireJustificationAnnonce(a, criteres));
                sb.append(idx++).append(") \"").append(a.getTitre()).append("\" (").append(exp).append(") ; ");
            }
        }
        return sb.toString().trim();
    }

    private String construireReponseAssistant(
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> annonces,
            Map<String, String> explications,
            String diagnosticBlocage
    ) {
        StringBuilder sb = new StringBuilder();

        boolean aucunCritere = criteres.getType() == null
                && criteres.getModeTransaction() == null
                && criteres.getVille() == null
                && criteres.getQuartier() == null
                && (criteres.getQuartiers() == null || criteres.getQuartiers().isEmpty())
                && criteres.getBudgetMax() == null
                && criteres.getBudgetMin() == null
                && criteres.getNombrePieces() == null
                && criteres.getNombreChambres() == null
                && criteres.getMeuble() == null
                && criteres.getSuperficieMin() == null
                && criteres.getSuperficieMax() == null
                && criteres.getEtage() == null
                && criteres.getAscenseur() == null
                && criteres.getAnimauxAcceptes() == null;

        if (aucunCritere && annonces.isEmpty()) {
            sb.append("Bonjour ! Je suis Samba, votre conseiller immobilier E-Courtier Sénégal. ");
            if (criteres.getQuestionDeRelance() != null) {
                sb.append(criteres.getQuestionDeRelance());
            } else {
                sb.append("Pour vous proposer des logements vérifiés, pouvez-vous m'indiquer la ville ou le quartier souhaité, le type de bien (chambre, studio, appartement, villa) et votre budget maximum en FCFA ?");
            }
            return sb.toString().trim();
        }

        sb.append("J'ai analysé votre demande");
        List<String> details = new ArrayList<>();
        if (criteres.getType() != null) {
            details.add("type : " + criteres.getType().name().toLowerCase(Locale.ROOT));
        }
        if (criteres.getModeTransaction() != null) {
            details.add("transaction : " + (criteres.getModeTransaction().name().equals("VENTE") ? "achat/vente" : "location"));
        }
        if (criteres.getQuartiers() != null && criteres.getQuartiers().size() > 1) {
            details.add("quartiers : " + String.join(" ou ", criteres.getQuartiers()));
        } else if (criteres.getQuartier() != null) {
            details.add("quartier : " + criteres.getQuartier());
        }
        if (criteres.getVille() != null) {
            details.add("ville : " + criteres.getVille());
        }
        if (criteres.getNombrePieces() != null) {
            details.add(criteres.getNombrePieces() + " pièce(s)");
        }
        if (criteres.getNombreChambres() != null) {
            details.add(criteres.getNombreChambres() + " chambre(s)");
        }
        if (criteres.getBudgetMax() != null) {
            details.add("budget max : " + criteres.getBudgetMax().toPlainString() + " FCFA");
        }
        if (criteres.getMeuble() != null) {
            details.add(criteres.getMeuble() ? "meublé" : "non meublé");
        }
        if (criteres.getSuperficieMin() != null) {
            details.add("surface min : " + criteres.getSuperficieMin().intValue() + " m²");
        }
        if (criteres.getEtage() != null) {
            details.add(criteres.getEtage() == 0 ? "RDC" : "étage " + criteres.getEtage());
        }
        if (Boolean.TRUE.equals(criteres.getAscenseur())) {
            details.add("avec ascenseur");
        }
        if (criteres.getAnimauxAcceptes() != null) {
            details.add(criteres.getAnimauxAcceptes() ? "animaux acceptés" : "sans animaux");
        }

        if (!details.isEmpty()) {
            sb.append(" (").append(String.join(", ", details)).append(")");
        }
        sb.append(". ");

        if (annonces.isEmpty()) {
            sb.append("Malheureusement, aucune annonce vérifiée ne correspond exactement à ces critères pour le moment. ");
            if (diagnosticBlocage != null && !diagnosticBlocage.isBlank()) {
                sb.append(diagnosticBlocage).append(" ");
            }
            if (criteres.getQuestionDeRelance() != null) {
                sb.append(criteres.getQuestionDeRelance());
            } else {
                sb.append("Souhaitez-vous élargir votre budget ou explorer un quartier voisin ?");
            }
        } else {
            sb.append("Voici ").append(annonces.size()).append(" annonce(s) vérifiée(s) et disponible(s) classée(s) par pertinence : ");
            AnnonceResponseDTO top = annonces.get(0);
            String topJustification = explications != null
                    ? explications.getOrDefault(top.getId().toString(), construireJustificationAnnonce(top, criteres))
                    : construireJustificationAnnonce(top, criteres);
            sb.append("\"").append(top.getTitre()).append("\" (").append(topJustification).append("). ");
            if (criteres.getQuestionDeRelance() != null) {
                sb.append(criteres.getQuestionDeRelance());
            }
        }

        if (Boolean.TRUE.equals(criteres.getModeSecours())) {
            sb.append(" (Mode secours local activé : recherche effectuée directement sur nos annonces vérifiées.)");
        }

        return sb.toString().trim();
    }

    private String serializeCriteres(CriteresExtraitsDTO criteres) {
        try {
            return objectMapper.writeValueAsString(criteres);
        } catch (JsonProcessingException e) {
            log.warn("Erreur lors de la sérialisation des critères extraits", e);
            return "{}";
        }
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String norm = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return norm.replace("œ", "oe").replace("æ", "ae");
    }
}
