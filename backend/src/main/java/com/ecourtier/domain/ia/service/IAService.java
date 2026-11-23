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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.Clock;
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

    public static final String IA_SESSION_HEADER_NAME = "X-IA-Session-Token";
    public static final int IA_SESSION_DURATION_DAYS = 7;
    private static final int MAX_USER_MESSAGE_LENGTH = 1000;
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
        String headerToken = request.getHeader(IA_SESSION_HEADER_NAME);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken.trim();
        }
        return null;
    }

    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail) {
        return creerConversationInternal(userEmail);
    }

    /**
     * Surcharge conservée pour compatibilité de signature : tout jeton fourni par l'appelant est
     * systématiquement ignoré au profit d'un jeton aléatoire généré côté serveur (REV-02).
     */
    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail, String ignoredClientToken) {
        return creerConversationInternal(userEmail);
    }

    @Transactional
    public ConversationCreateResponseDTO creerConversation(String userEmail, HttpServletRequest request, HttpServletResponse response) {
        // Ignorer strictement tout X-IA-Session-Token fourni par le client à la création (REV-02)
        return creerConversationInternal(userEmail);
    }

    private ConversationCreateResponseDTO creerConversationInternal(String userEmail) {
        UtilisateurEntity user = null;
        boolean isAuthentifie = (userEmail != null && !userEmail.trim().isEmpty() && !"anonymousUser".equals(userEmail));
        if (isAuthentifie) {
            user = utilisateurRepository.findByEmail(userEmail).orElse(null);
        }

        String sessionTokenHash = null;
        LocalDateTime expiration = null;
        LocalDateTime now = nowDakar();
        String issuedTokenForClient = null;

        if (user == null) {
            String serverToken = generateSecureToken();
            issuedTokenForClient = serverToken;
            sessionTokenHash = hashToken(serverToken);
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
                .sessionToken(issuedTokenForClient)
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
            if (request.getMessage().length() > MAX_USER_MESSAGE_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne doit pas dépasser 1000 caractères");
            }

            long messageCount = messageIARepository.countByConversationId(conversationId);
            if (messageCount >= 60) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cette conversation a atteint la limite maximale de messages. Veuillez démarrer une nouvelle conversation."
                );
            }

            List<MessageIAEntity> historique = messageIARepository.findByConversationIdOrderByOrdreAscDateEnvoiAscIdAsc(conversationId);
            return extraireDerniersCriteresConnus(historique);
        });

        // =========================================================================
        // ÉTAPE 2 : Extraction NLU & appel fournisseur LLM HORS transaction SQL
        // =========================================================================
        long tExtractStart = System.nanoTime();
        CriteresExtraitsDTO nouveauxCriteres = llmExtractionService.extractCriteria(request.getMessage(), criteresPrecedents);
        String intentionSpeciale = nouveauxCriteres.getIntentionSpeciale();
        boolean isSpecialPreservedQuestion = "FAQ_VERIFICATION_JURIDIQUE".equals(intentionSpeciale)
                || "FAQ_DOCUMENTS".equals(intentionSpeciale)
                || "QUESTION_GENERALE".equals(intentionSpeciale);
        CriteresExtraitsDTO criteresFinaux = isSpecialPreservedQuestion
                ? preserveCriteriaForSpecialQuestion(criteresPrecedents, nouveauxCriteres, intentionSpeciale)
                : fusionnerCriteres(criteresPrecedents, nouveauxCriteres);
        long tempsExtractionMs = (System.nanoTime() - tExtractStart) / 1_000_000L;

        boolean horsSujet = Boolean.TRUE.equals(nouveauxCriteres.getHorsSujet());
        boolean isFaqLegal = "FAQ_VERIFICATION_JURIDIQUE".equals(intentionSpeciale);
        boolean isFaqDocuments = "FAQ_DOCUMENTS".equals(intentionSpeciale);
        boolean isGeneralQuestion = "QUESTION_GENERALE".equals(intentionSpeciale);
        boolean isExplication = "EXPLICATION_RECOMMANDATION".equals(intentionSpeciale);

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

        boolean demandeExploratoire = !isGeneralQuestion
                && !isFaqDocuments
                && !isFaqLegal
                && !"LISTE_DISPONIBLE".equals(intentionSpeciale)
                && !isExplication
                && criteresFinaux.getType() == null
                && criteresFinaux.getBudgetMax() == null
                && criteresFinaux.getBudgetMin() == null;

        // =========================================================================
        // ÉTAPE 3 : Recherche en lecture seule des annonces réelles et vérifiées
        // =========================================================================
        long tSearchStart = System.nanoTime();
        SearchExecutionResult searchResult;
        if (horsSujet
                || isFaqDocuments
                || isFaqLegal
                || isGeneralQuestion
                || ((aucunCritere || demandeExploratoire) && !isExplication)) {
            searchResult = new SearchExecutionResult(List.of(), Map.of(), null);
        } else if (isExplication
                && criteresPrecedents != null
                && criteresPrecedents.getAnnoncesSuggereesIds() != null
                && !criteresPrecedents.getAnnoncesSuggereesIds().isEmpty()) {
            searchResult = executeInReadTx(() -> chargerAnnoncesParIdsAvecExplications(criteresPrecedents.getAnnoncesSuggereesIds(), criteresFinaux));
        } else {
            searchResult = executeInReadTx(() -> rechercherEtClasserAnnoncesPertinentes(criteresFinaux));
        }
        List<AnnonceResponseDTO> catalogPreviewForLlm = (!horsSujet && !isFaqLegal && searchResult.annonces().isEmpty()
                && (isGeneralQuestion || llmExtractionService.isExternalGeminiActive()))
                ? executeInReadTx(() -> rechercherApercuCatalogueDisponible(criteresFinaux))
                : List.of();
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
        } else if (isFaqLegal) {
            if (!Boolean.TRUE.equals(nouveauxCriteres.getModeSecours()) && llmExtractionService.isExternalGeminiActive()) {
                LlmExtractionService.GenerationOutcome genOutcome = llmExtractionService.generateConversationalResponseWithDiagnostic(
                        request.getMessage(), criteresFinaux, List.of(), null);
                if (genOutcome.responseText() != null && !genOutcome.responseText().isBlank()) {
                    texteReponse = genOutcome.responseText();
                } else {
                    criteresFinaux.setModeSecours(true);
                    criteresFinaux.setFournisseurIa("fallback-local");
                    criteresFinaux.setMotifSecours(genOutcome.motifSecours() != null ? genOutcome.motifSecours() : "ERREUR_HTTP");
                    texteReponse = construireReponseTransparenceJuridique();
                }
            } else {
                String resolvedMotif = nouveauxCriteres.getMotifSecours();
                if (resolvedMotif == null || resolvedMotif.isBlank()) {
                    resolvedMotif = llmExtractionService.resolveConfigFallbackMotif();
                }
                criteresFinaux.setModeSecours(true);
                criteresFinaux.setFournisseurIa("fallback-local");
                criteresFinaux.setMotifSecours(resolvedMotif != null ? resolvedMotif : "CLE_ABSENTE");
                texteReponse = construireReponseTransparenceJuridique();
            }
        } else if (!Boolean.TRUE.equals(nouveauxCriteres.getModeSecours()) && llmExtractionService.isExternalGeminiActive()) {
            List<AnnonceResponseDTO> promptAnnonces = !annoncesSuggerees.isEmpty() ? annoncesSuggerees : catalogPreviewForLlm;
            boolean strictSearchMiss = annoncesSuggerees.isEmpty() && !aucunCritere && !demandeExploratoire && !isGeneralQuestion && !isFaqDocuments;
            String promptGuidance = buildLlmGuidance(criteresFinaux.getQuestionDeRelance(), strictSearchMiss ? diagnosticCriteresBloquants : null);
            LlmExtractionService.GenerationOutcome genOutcome = llmExtractionService.generateConversationalResponseWithDiagnostic(
                    request.getMessage(),
                    criteresFinaux,
                    promptAnnonces,
                    promptGuidance
            );
            String llmResponse = genOutcome.responseText();
            if (llmResponse != null && !llmResponse.isBlank()) {
                texteReponse = llmResponse;
                if (strictSearchMiss && diagnosticCriteresBloquants != null && !texteReponse.contains(diagnosticCriteresBloquants)) {
                    texteReponse = texteReponse + " " + diagnosticCriteresBloquants;
                }
            } else {
                criteresFinaux.setModeSecours(true);
                criteresFinaux.setFournisseurIa("fallback-local");
                criteresFinaux.setMotifSecours(genOutcome.motifSecours());
                texteReponse = buildLocalFallbackResponse(
                        request.getMessage(), intentionSpeciale, criteresFinaux, annoncesSuggerees,
                        explicationsAnnonces, diagnosticCriteresBloquants, aucunCritere || demandeExploratoire, catalogPreviewForLlm
                );
            }
        } else {
            criteresFinaux.setModeSecours(true);
            criteresFinaux.setFournisseurIa("fallback-local");
            if (criteresFinaux.getMotifSecours() == null || criteresFinaux.getMotifSecours().isBlank()) {
                String configMotif = llmExtractionService.resolveConfigFallbackMotif();
                String resolved = (nouveauxCriteres.getMotifSecours() != null && !nouveauxCriteres.getMotifSecours().isBlank())
                        ? nouveauxCriteres.getMotifSecours()
                        : configMotif;
                criteresFinaux.setMotifSecours(resolved != null ? resolved : "CLE_ABSENTE");
            }
            texteReponse = buildLocalFallbackResponse(
                    request.getMessage(), intentionSpeciale, criteresFinaux, annoncesSuggerees,
                    explicationsAnnonces, diagnosticCriteresBloquants, aucunCritere || demandeExploratoire, catalogPreviewForLlm
            );
        }
        final String texteReponseFinal = texteReponse;
        long tempsGenerationMs = (System.nanoTime() - tGenStart) / 1_000_000L;

        // =========================================================================
        // ÉTAPE 5 : Transaction courte d'écriture pour persister l'échange (ordre déterministe)
        // =========================================================================
        long tPersistStart = System.nanoTime();
        String criteresJson = serializeCriteres(criteresFinaux);
        LocalDateTime now = nowDakar();

        MessageIAEntity savedAssistantMsg;
        try {
            savedAssistantMsg = executeInWriteTx(() -> {
                // Verrou pessimiste sur la conversation pour sérialiser l'allocation d'ordre (REV-03)
                ConversationIAEntity conversation = conversationIARepository.findByIdForUpdate(conversationId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation IA introuvable"));

                verifierDroitsConversation(conversation, emailUtilisateur, rawSessionToken);
                long currentCount = messageIARepository.countByConversationId(conversationId);
                if (currentCount >= 60) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Cette conversation a atteint la limite maximale de messages. Veuillez démarrer une nouvelle conversation."
                    );
                }

                long nextOrdre = messageIARepository.findMaxOrdreByConversationId(conversationId) + 1L;

                MessageIAEntity userMessageEntity = MessageIAEntity.builder()
                        .conversation(conversation)
                        .role(RoleMessageIA.UTILISATEUR)
                        .ordre(nextOrdre)
                        .contenu(request.getMessage().trim())
                        .dateEnvoi(now)
                        .build();
                messageIARepository.save(userMessageEntity);

                MessageIAEntity assistantMessageEntity = MessageIAEntity.builder()
                        .conversation(conversation)
                        .role(RoleMessageIA.ASSISTANT)
                        .ordre(nextOrdre + 1L)
                        .contenu(texteReponseFinal)
                        .criteresExtraits(criteresJson)
                        .nombreAnnoncesSuggerees(annoncesSuggerees.size())
                        .dateEnvoi(now)
                        .build();
                return messageIARepository.save(assistantMessageEntity);
            });
        } catch (PessimisticLockingFailureException | DataIntegrityViolationException ex) {
            log.warn("Conflit d'écriture concurrente détecté sur la conversation {} ({})",
                    conversationId, ex.getClass().getSimpleName());
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Une écriture simultanée est en cours sur cette conversation. Veuillez réessayer.",
                    ex
            );
        }
        long tempsPersistanceMs = (System.nanoTime() - tPersistStart) / 1_000_000L;

        // =========================================================================
        // ÉTAPE 6 : Construction de la réponse et journalisation des métriques
        // =========================================================================
        long tempsTotalMs = (System.nanoTime() - tTotalStart) / 1_000_000L;
        long tempsLlmMs = tempsExtractionMs + tempsGenerationMs;

        log.info("IA_MESSAGE_PROCESSED convId={} provider={} fallback={} motif={} totalMs={} llmMs={} extractMs={} searchMs={} persistMs={} results={}",
                conversationId,
                criteresFinaux.getFournisseurIa(),
                criteresFinaux.getModeSecours(),
                criteresFinaux.getMotifSecours(),
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
                .metriques(MessageIaResponseDTO.IaMetriquesDTO.builder()
                        .extractMs(tempsExtractionMs)
                        .searchMs(tempsRechercheMs)
                        .generationMs(tempsGenerationMs)
                        .llmMs(tempsLlmMs)
                        .totalMs(tempsTotalMs)
                        .build())
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

        List<MessageIAEntity> messages = messageIARepository.findByConversationIdOrderByOrdreAscDateEnvoiAscIdAsc(conversationId);

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
                    .ordre(m.getOrdre())
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

    private CriteresExtraitsDTO preserveCriteriaForSpecialQuestion(
            CriteresExtraitsDTO precedents,
            CriteresExtraitsDTO nouveaux,
            String intentionSpeciale
    ) {
        if (precedents == null) {
            return nouveaux;
        }
        return CriteresExtraitsDTO.builder()
                .type(precedents.getType())
                .modeTransaction(precedents.getModeTransaction())
                .ville(precedents.getVille())
                .quartier(precedents.getQuartier())
                .quartiers(precedents.getQuartiers())
                .budgetMin(precedents.getBudgetMin())
                .budgetMax(precedents.getBudgetMax())
                .nombrePieces(precedents.getNombrePieces())
                .nombreChambres(precedents.getNombreChambres())
                .etage(precedents.getEtage())
                .ascenseur(precedents.getAscenseur())
                .meuble(precedents.getMeuble())
                .animauxAcceptes(precedents.getAnimauxAcceptes())
                .superficieMin(precedents.getSuperficieMin())
                .superficieMax(precedents.getSuperficieMax())
                .equipements(precedents.getEquipements())
                .proximites(precedents.getProximites())
                .preferencesSouples(precedents.getPreferencesSouples())
                .attentesComplementaires(precedents.getAttentesComplementaires())
                .precisionsAClarifier(precedents.getPrecisionsAClarifier())
                .annoncesSuggereesIds(precedents.getAnnoncesSuggereesIds())
                .explicationsAnnonces(precedents.getExplicationsAnnonces())
                .confiance(nouveaux.getConfiance() != null ? nouveaux.getConfiance() : precedents.getConfiance())
                .fournisseurIa(nouveaux.getFournisseurIa())
                .modeSecours(nouveaux.getModeSecours())
                .motifSecours(nouveaux.getMotifSecours())
                .horsSujet(false)
                .intentionSpeciale(intentionSpeciale)
                .questionDeRelance(null)
                .build();
    }

    private CriteresExtraitsDTO fusionnerCriteres(CriteresExtraitsDTO precedents, CriteresExtraitsDTO nouveaux) {
        if (precedents == null) {
            return nouveaux;
        }
        if (nouveaux == null) {
            return precedents;
        }

        Set<String> removals = nouveaux.getCriteresASupprimer() != null ? nouveaux.getCriteresASupprimer() : Set.of();
        boolean villeChanged = nouveaux.getVille() != null
                && precedents.getVille() != null
                && !normalizeText(nouveaux.getVille()).equals(normalizeText(precedents.getVille()));
        boolean newCityAndTypeSearch = villeChanged && nouveaux.getType() != null;

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
        } else if (villeChanged) {
            mergedQuartier = null;
            mergedQuartiers = null;
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

        Boolean mergedMeuble = removals.contains("meuble")
                ? null
                : (nouveaux.getMeuble() != null ? nouveaux.getMeuble() : (newCityAndTypeSearch ? null : precedents.getMeuble()));

        List<String> mergedPreferencesSouples = mergeStringLists(
                precedents.getPreferencesSouples(),
                nouveaux.getPreferencesSouples()
        );
        if (mergedPreferencesSouples != null && (Boolean.FALSE.equals(mergedMeuble) || removals.contains("meuble"))) {
            mergedPreferencesSouples = mergedPreferencesSouples.stream()
                    .filter(p -> !normalizeText(p).contains("meubl"))
                    .toList();
            if (mergedPreferencesSouples.isEmpty()) {
                mergedPreferencesSouples = null;
            }
        }

        List<String> mergedAttentes = mergeStringLists(
                precedents.getAttentesComplementaires(),
                nouveaux.getAttentesComplementaires()
        );

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
                .etage(removals.contains("etage") ? null : (nouveaux.getEtage() != null ? nouveaux.getEtage() : (newCityAndTypeSearch ? null : precedents.getEtage())))
                .ascenseur(nouveaux.getAscenseur() != null ? nouveaux.getAscenseur() : (newCityAndTypeSearch ? null : precedents.getAscenseur()))
                .meuble(mergedMeuble)
                .animauxAcceptes(nouveaux.getAnimauxAcceptes() != null ? nouveaux.getAnimauxAcceptes() : precedents.getAnimauxAcceptes())
                .superficieMin(removals.contains("superficie") ? null : (nouveaux.getSuperficieMin() != null ? nouveaux.getSuperficieMin() : (newCityAndTypeSearch ? null : precedents.getSuperficieMin())))
                .superficieMax(removals.contains("superficie") ? null : (nouveaux.getSuperficieMax() != null ? nouveaux.getSuperficieMax() : (newCityAndTypeSearch ? null : precedents.getSuperficieMax())))
                .equipements(mergedEquipements)
                .proximites(mergedProximites)
                .preferencesSouples(mergedPreferencesSouples)
                .attentesComplementaires(mergedAttentes)
                .confiance(nouveaux.getConfiance() != null ? nouveaux.getConfiance() : precedents.getConfiance())
                .fournisseurIa(nouveaux.getFournisseurIa())
                .modeSecours(nouveaux.getModeSecours())
                .motifSecours(nouveaux.getMotifSecours())
                .horsSujet(nouveaux.getHorsSujet())
                .intentionSpeciale(nouveaux.getIntentionSpeciale())
                .demandeQuestionsComplementaires(nouveaux.getDemandeQuestionsComplementaires())
                .besoinIndecis(nouveaux.getBesoinIndecis())
                .build();

        merged.setPrecisionsAClarifier(computeRemainingPrecisionsAClarifier(merged));
        String questionRelance = resoudreQuestionDeRelanceMultiTours(precedents, nouveaux, merged);
        merged.setQuestionDeRelance(questionRelance);

        return merged;
    }

    private List<String> mergeStringLists(List<String> first, List<String> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return null;
        }
        Set<String> set = new LinkedHashSet<>();
        if (first != null) {
            first.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).forEach(set::add);
        }
        if (second != null) {
            second.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).forEach(set::add);
        }
        return set.isEmpty() ? null : new ArrayList<>(set);
    }

    private List<String> computeRemainingPrecisionsAClarifier(CriteresExtraitsDTO merged) {
        List<String> missing = new ArrayList<>();
        if (merged.getType() == null) {
            missing.add("type de logement souhaité (chambre, studio, appartement, villa)");
        }
        if (merged.getBudgetMax() == null && merged.getBudgetMin() == null) {
            missing.add("budget maximum en FCFA");
        }
        boolean hasLoc = merged.getVille() != null || merged.getQuartier() != null
                || (merged.getQuartiers() != null && !merged.getQuartiers().isEmpty());
        if (!hasLoc) {
            missing.add("ville ou quartier souhaité");
        }
        if (merged.getAttentesComplementaires() != null
                && merged.getAttentesComplementaires().stream().anyMatch(a -> normalizeText(a).contains("terrain"))
                && merged.getSuperficieMin() == null) {
            missing.add("superficie minimale souhaitée (en m²)");
        }
        return missing.isEmpty() ? null : missing;
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

        if (Boolean.TRUE.equals(nouveaux.getBesoinIndecis())
                && nouveaux.getQuestionDeRelance() != null && !nouveaux.getQuestionDeRelance().isBlank()) {
            return nouveaux.getQuestionDeRelance();
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
        boolean mentionsCalm = merged.getPreferencesSouples() != null
                && merged.getPreferencesSouples().stream().anyMatch(p -> normalizeText(p).contains("calme"));

        if (!hasBudget && !hasType) {
            return mentionsCalm
                    ? "Pour orienter votre recherche dans un secteur calme, quel est votre budget maximum (en FCFA) et quel type de bien (studio, appartement, villa, chambre) recherchez-vous ?"
                    : "Pour affiner ma recherche, quel est votre budget maximum (en FCFA) et quel type de bien (studio, appartement, villa, chambre) recherchez-vous ?";
        }
        if (!hasBudget) {
            return "Quel est votre budget maximum en FCFA pour ce bien ?";
        }
        if (!hasLocalisation && !hasType) {
            return "Dans quelle ville ou quel quartier recherchez-vous et quel type de logement souhaitez-vous ?";
        }
        if (!hasType) {
            return "Quel type de bien (chambre, studio, appartement ou villa) recherchez-vous ?";
        }
        if (!hasLocalisation) {
            return mentionsCalm
                    ? "Dans quelle ville ou quel quartier calme (ex: Dakar, Mermoz, Almadies, Ziguinchor, Boucotte) souhaitez-vous habiter ?"
                    : "Dans quelle ville ou quel quartier (ex: Dakar, Mermoz, Almadies, Ziguinchor, Boucotte) souhaitez-vous habiter ?";
        }
        if (merged.getAttentesComplementaires() != null
                && merged.getAttentesComplementaires().stream().anyMatch(a -> normalizeText(a).contains("terrain"))
                && merged.getSuperficieMin() == null) {
            return "Quelle superficie minimale approximative (en m²) souhaitez-vous pour le terrain ou le logement ?";
        }
        if (Boolean.TRUE.equals(nouveaux.getDemandeQuestionsComplementaires())
                && nouveaux.getQuestionDeRelance() != null && !nouveaux.getQuestionDeRelance().isBlank()) {
            return nouveaux.getQuestionDeRelance();
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

    private String buildLlmGuidance(String questionDeRelance, String diagnosticBlocage) {
        if (diagnosticBlocage != null && questionDeRelance != null && !questionDeRelance.isBlank()) {
            return diagnosticBlocage + " " + questionDeRelance;
        }
        return diagnosticBlocage != null ? diagnosticBlocage : questionDeRelance;
    }

    private String buildLocalFallbackResponse(
            String userMessage,
            String intentionSpeciale,
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> annoncesSuggerees,
            Map<String, String> explicationsAnnonces,
            String diagnosticCriteresBloquants,
            boolean demandeExploratoire,
            List<AnnonceResponseDTO> catalogPreview
    ) {
        if ("FAQ_DOCUMENTS".equals(intentionSpeciale)) {
            return construireReponseFaqDocuments(criteres);
        }
        if ("EXPLICATION_RECOMMANDATION".equals(intentionSpeciale)) {
            return construireReponseExplicationRecommandation(annoncesSuggerees, explicationsAnnonces, criteres);
        }
        if ("QUESTION_GENERALE".equals(intentionSpeciale)) {
            return construireReponseQuestionGenerale(userMessage, criteres, catalogPreview);
        }
        return construireReponseAssistant(
                criteres,
                annoncesSuggerees,
                explicationsAnnonces,
                diagnosticCriteresBloquants,
                demandeExploratoire
        );
    }

    private List<AnnonceEntity> loadVerifiedAvailablePool() {
        return annonceRepository
                .findBySupprimeFalseAndStatutPublicationAndStatutVerification(
                        StatutPublication.DISPONIBLE,
                        StatutVerification.VERIFIEE
                ).stream()
                .filter(a -> !a.isSupprime())
                .filter(a -> a.getStatutPublication() == StatutPublication.DISPONIBLE)
                .filter(a -> a.getStatutVerification() == StatutVerification.VERIFIEE)
                .toList();
    }

    private List<AnnonceResponseDTO> rechercherApercuCatalogueDisponible(CriteresExtraitsDTO criteres) {
        List<AnnonceEntity> pool = loadVerifiedAvailablePool();
        if (pool.isEmpty()) {
            return List.of();
        }
        List<AnnonceEntity> prioritized = pool.stream()
                .filter(a -> criteres == null
                        || ((criteres.getVille() == null || (a.getVille() != null && normalizeText(a.getVille()).contains(normalizeText(criteres.getVille()))))
                        && (criteres.getType() == null || a.getType() == criteres.getType())))
                .limit(5)
                .toList();
        List<AnnonceEntity> selection = !prioritized.isEmpty() ? prioritized : pool.stream().limit(5).toList();
        return selection.stream()
                .map(entity -> sanitizeAnnonceForAssistant(annonceMapper.toResponseDTO(entity)))
                .toList();
    }

    private SearchExecutionResult rechercherEtClasserAnnoncesPertinentes(CriteresExtraitsDTO criteres) {
        List<AnnonceEntity> poolVerifieDisponible = loadVerifiedAvailablePool();

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
        String haystack = normalizeText(
                (a.getTitre() != null ? a.getTitre() : "")
                        + " "
                        + (a.getDescription() != null ? a.getDescription() : "")
                        + " "
                        + (a.getQuartier() != null ? a.getQuartier() : "")
        );
        if (c.getPreferencesSouples() != null) {
            for (String pref : c.getPreferencesSouples()) {
                String normPref = normalizeText(pref);
                if (normPref.contains("meubl") && a.isMeuble()) {
                    score += 15;
                }
                if (normPref.contains("calme") && (haystack.contains("calme")
                        || haystack.contains("residentiel")
                        || haystack.contains("paisible")
                        || haystack.contains("tranquille")
                        || haystack.contains("sacre-coeur")
                        || haystack.contains("almadies")
                        || haystack.contains("mermoz")
                        || haystack.contains("fann"))) {
                    score += 10;
                }
            }
        }
        if (c.getAttentesComplementaires() != null) {
            for (String attente : c.getAttentesComplementaires()) {
                String normAtt = normalizeText(attente);
                if (normAtt.contains("cour") && haystack.contains("cour")) {
                    score += 8;
                }
                if (normAtt.contains("jardin") && haystack.contains("jardin")) {
                    score += 8;
                }
                if (normAtt.contains("cuisine") && haystack.contains("cuisine")) {
                    score += 8;
                }
                if (normAtt.contains("terrain") && (haystack.contains("terrain") || (a.getSuperficie() != null && a.getSuperficie() >= 300))) {
                    score += 8;
                }
                if ((normAtt.contains("balcon") || normAtt.contains("terrasse"))
                        && (haystack.contains("balcon") || haystack.contains("terrasse"))) {
                    score += 6;
                }
            }
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
                points.add("prix de " + formatFcfa(a.getPrix()) + " respectant votre budget de " + formatFcfa(c.getBudgetMax()));
            } else {
                points.add("prix de " + formatFcfa(a.getPrix()));
            }
        }
        if (a.getNombrePieces() != null) {
            points.add(a.getNombrePieces() + " pièce(s)");
        }
        if (a.getSuperficie() != null) {
            points.add(a.getSuperficie().intValue() + " m²");
        }
        if (a.isMeuble()) {
            boolean softMeuble = c != null && c.getPreferencesSouples() != null
                    && c.getPreferencesSouples().stream().anyMatch(p -> normalizeText(p).contains("meubl"));
            points.add(softMeuble ? "meublé (selon votre préférence)" : "meublé");
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
                    blocages.add("le budget maximum de " + formatFcfa(c.getBudgetMax())
                            + " est inférieur aux tarifs des biens vérifiés de ce secteur (à partir de "
                            + formatFcfa(cheapest.getPrix()) + " pour \"" + cheapest.getTitre() + "\")");
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
        if (c.getVille() != null && blocages.isEmpty()) {
            List<AnnonceEntity> inVille = pool.stream()
                    .filter(a -> a.getVille() != null && normalizeText(a.getVille()).contains(normalizeText(c.getVille())))
                    .toList();
            if (!inVille.isEmpty()) {
                AnnonceEntity minInVille = inVille.stream()
                        .filter(a -> a.getPrix() != null)
                        .min(Comparator.comparing(AnnonceEntity::getPrix))
                        .orElse(null);
                String detailPrix = minInVille != null ? " (à partir de " + formatFcfa(minInVille.getPrix()) + " pour \"" + minInVille.getTitre() + "\")" : "";
                blocages.add(inVille.size() + " autre(s) logement(s) vérifié(s) sont disponibles à " + c.getVille() + detailPrix);
            }
        }
        if (c.getType() != null) {
            List<AnnonceEntity> sameTypeElsewhere = pool.stream()
                    .filter(a -> a.getType() == c.getType())
                    .filter(a -> c.getVille() == null || a.getVille() == null || !normalizeText(a.getVille()).contains(normalizeText(c.getVille())))
                    .toList();
            if (!sameTypeElsewhere.isEmpty()) {
                String cities = sameTypeElsewhere.stream()
                        .map(AnnonceEntity::getVille)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.joining(", "));
                if (!cities.isBlank()) {
                    blocages.add(sameTypeElsewhere.size() + " " + c.getType().name().toLowerCase(Locale.ROOT)
                            + "(s) vérifié(s) sont disponibles dans d'autres secteurs (" + cities + ")");
                }
            }
        }
        if (blocages.isEmpty()) {
            return null;
        }
        return "Diagnostic : " + String.join(" ; ", blocages) + ".";
    }

    private String construireReponseQuestionGenerale(
            String userMessage,
            CriteresExtraitsDTO criteres,
            List<AnnonceResponseDTO> catalogPreview
    ) {
        String norm = normalizeText(userMessage);
        StringBuilder sb = new StringBuilder();
        if (norm.contains("caution") || norm.contains("mois")) {
            sb.append("Sur E-Courtier Sénégal, la caution et le premier loyer se règlent uniquement après une visite réelle confirmée et la génération du contrat de bail sur la plateforme (ne versez jamais d'argent avant visite). ");
        } else if (norm.contains("fonctionne") || norm.contains("marche") || norm.contains("c'est quoi")) {
            sb.append("E-Courtier Sénégal vérifie administrativement les annonces et l'identité des propriétaires avant publication, organise les demandes de visite et sécurise le contrat de bail numérique. ");
        } else if (norm.contains("quartier")) {
            sb.append("À Dakar, les secteurs résidentiels calmes prisés incluent Mermoz, Sacré-Cœur, Almadies et Fann ; à Ziguinchor, vous pouvez explorer Nema, Boucotte ou Kenia selon votre budget. ");
        } else {
            sb.append("Bonjour ! Je suis Samba, votre conseiller immobilier E-Courtier Sénégal. Je peux vous aider à trouver un logement vérifié (chambre, studio, appartement ou villa), comparer des quartiers ou répondre à vos questions sur la location et l'achat au Sénégal. ");
        }
        if (catalogPreview != null && !catalogPreview.isEmpty()) {
            AnnonceResponseDTO first = catalogPreview.get(0);
            sb.append("Actuellement, ").append(catalogPreview.size()).append(" bien(s) vérifié(s) sont notamment disponibles, par exemple \"")
                    .append(first.getTitre()).append("\" à ").append(first.getQuartier()).append(" (").append(first.getVille())
                    .append(", ").append(formatFcfa(first.getPrix())).append("). ");
        }
        sb.append("Indiquez-moi votre ville, type de bien ou budget maximum en FCFA pour lancer une recherche ciblée.");
        if (Boolean.TRUE.equals(criteres != null ? criteres.getModeSecours() : null)) {
            sb.append(" (Mode secours local activé : réponse basée sur nos règles locales et notre catalogue vérifié.)");
        }
        return sb.toString().trim();
    }

    private String construireReponseTransparenceJuridique() {
        return "La fiabilité des annonces sur E-Courtier Sénégal repose sur des contrôles concrets et transparents : "
                + "1) Vérification administrative préalable : seules les annonces validées par l'administration avec le statut VERIFIEE et disponibles sont recommandées par l'assistant ; "
                + "2) Contrôle de l'identité et des justificatifs : l'identité du bailleur ou mandataire et les pièces déposées sur la plateforme sont vérifiées avant publication ; "
                + "3) Documents usuels pour louer au Sénégal : pièce d'identité valide (CNI CEDEAO ou passeport), justificatifs de revenus ou d'activité, contrat de bail écrit et quittance ou reçu de caution encadré ; "
                + "4) Précautions indispensables avant tout paiement : effectuez toujours une visite physique du bien, demandez à voir les originaux (titre ou mandat) sur place, relisez attentivement le contrat et ne versez aucune somme hors parcours traçable et sans visite confirmée ; "
                + "5) Traçabilité des échanges : vos messages, rendez-vous et transactions enregistrés sur la plateforme conservent un historique vérifiable ; "
                + "6) Limites d'une plateforme numérique : E-Courtier sécurise le filtrage initial mais ne remplace ni l'inspection physique des lieux ni, pour un achat immobilier, l'intervention d'un notaire pour l'acte authentique et la vérification foncière.";
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
            String diagnosticBlocage,
            boolean demandeExploratoire
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
                && criteres.getAnimauxAcceptes() == null
                && (criteres.getEquipements() == null || criteres.getEquipements().isEmpty())
                && (criteres.getProximites() == null || criteres.getProximites().isEmpty())
                && (criteres.getPreferencesSouples() == null || criteres.getPreferencesSouples().isEmpty())
                && (criteres.getAttentesComplementaires() == null || criteres.getAttentesComplementaires().isEmpty());

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
            details.add("budget max : " + formatFcfa(criteres.getBudgetMax()));
        }
        if (criteres.getBudgetMin() != null) {
            details.add("budget min : " + formatFcfa(criteres.getBudgetMin()));
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
        if (criteres.getEquipements() != null && !criteres.getEquipements().isEmpty()) {
            details.add("équipements : " + criteres.getEquipements().stream()
                    .map(e -> e.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                    .collect(Collectors.joining(", ")));
        }
        if (criteres.getProximites() != null && !criteres.getProximites().isEmpty()) {
            details.add("proximités : " + criteres.getProximites().stream()
                    .map(p -> p.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                    .collect(Collectors.joining(", ")));
        }
        if (criteres.getAttentesComplementaires() != null && !criteres.getAttentesComplementaires().isEmpty()) {
            details.add("attentes : " + String.join(", ", criteres.getAttentesComplementaires()));
        }
        if (criteres.getPreferencesSouples() != null && !criteres.getPreferencesSouples().isEmpty()) {
            details.add("préférences : " + String.join(", ", criteres.getPreferencesSouples()));
        }

        if (!details.isEmpty()) {
            sb.append(" (").append(String.join(", ", details)).append(")");
        }
        sb.append(". ");

        if (annonces.isEmpty()) {
            if (demandeExploratoire) {
                if (criteres.getQuestionDeRelance() != null) {
                    sb.append(criteres.getQuestionDeRelance());
                } else {
                    sb.append("Pour lancer la recherche sur nos annonces vérifiées, précisez le type de bien et votre budget maximum en FCFA.");
                }
            } else {
                sb.append("Malheureusement, aucune annonce vérifiée ne correspond exactement à ces critères pour le moment. ");
                if (diagnosticBlocage != null && !diagnosticBlocage.isBlank()) {
                    sb.append(diagnosticBlocage).append(" ");
                }
                if (criteres.getQuestionDeRelance() != null) {
                    sb.append(criteres.getQuestionDeRelance());
                } else {
                    sb.append("Souhaitez-vous élargir votre budget ou explorer un quartier voisin ?");
                }
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

    private String formatFcfa(BigDecimal amount) {
        if (amount == null) {
            return "N/C";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        df.setGroupingUsed(true);
        return df.format(amount.longValue()) + " FCFA";
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
