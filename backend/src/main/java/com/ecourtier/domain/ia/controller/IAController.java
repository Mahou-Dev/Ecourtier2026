package com.ecourtier.domain.ia.controller;

import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.ConversationHistoryResponseDTO;
import com.ecourtier.domain.ia.dto.MessageIaResponseDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.service.IAService;
import com.ecourtier.domain.ia.service.IaRateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ia/conversations")
@RequiredArgsConstructor
@Tag(name = "Assistant IA Chatbot", description = "Endpoints de gestion du chatbot conversationnel intelligent et shortlist personnalisée")
public class IAController {

    private final IAService iaService;
    private final IaRateLimiterService iaRateLimiterService;

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Démarrer une nouvelle conversation avec l'assistant IA", description = "Accessible en mode anonyme ou authentifié avec contrôle de quota.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversation IA initialisée"),
            @ApiResponse(responseCode = "429", description = "Quota de requêtes IA dépassé")
    })
    public ResponseEntity<ConversationCreateResponseDTO> demarrerConversation(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        iaRateLimiterService.checkRateLimit(request, authentication);
        String email = authentication != null ? authentication.getName() : null;
        ConversationCreateResponseDTO body = iaService.creerConversation(email, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/messages")
    @SecurityRequirements
    @Operation(summary = "Envoyer un message à l'assistant IA", description = "Analyse le besoin en langage naturel, extrait les critères et retourne une shortlist d'annonces réelles en < 3s.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réponse de l'assistant IA et shortlist d'annonces"),
            @ApiResponse(responseCode = "400", description = "Message vide ou invalide"),
            @ApiResponse(responseCode = "403", description = "Accès refusé à la conversation d'un autre utilisateur"),
            @ApiResponse(responseCode = "404", description = "Conversation IA introuvable"),
            @ApiResponse(responseCode = "429", description = "Quota de requêtes IA dépassé")
    })
    public ResponseEntity<MessageIaResponseDTO> envoyerMessage(
            @PathVariable UUID id,
            @Valid @RequestBody MessageUserRequestDTO requestDto,
            HttpServletRequest request,
            Authentication authentication
    ) {
        iaRateLimiterService.checkRateLimit(request, authentication);
        String email = authentication != null ? authentication.getName() : null;
        MessageIaResponseDTO response = iaService.envoyerMessage(id, requestDto, email, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Obtenir l'historique complet d'une conversation IA", description = "Retourne la liste chronologique des messages et des critères extraits.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historique complet de la conversation IA"),
            @ApiResponse(responseCode = "403", description = "Accès refusé à la conversation d'un autre utilisateur"),
            @ApiResponse(responseCode = "404", description = "Conversation IA introuvable"),
            @ApiResponse(responseCode = "429", description = "Quota de requêtes IA dépassé")
    })
    public ResponseEntity<ConversationHistoryResponseDTO> obtenirHistorique(
            @PathVariable UUID id,
            HttpServletRequest request,
            Authentication authentication
    ) {
        iaRateLimiterService.checkRateLimit(request, authentication);
        String email = authentication != null ? authentication.getName() : null;
        ConversationHistoryResponseDTO history = iaService.obtenirHistorique(id, email, request);
        return ResponseEntity.ok(history);
    }
}
