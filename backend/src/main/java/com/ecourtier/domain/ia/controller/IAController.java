package com.ecourtier.domain.ia.controller;

import com.ecourtier.domain.ia.dto.ConversationCreateResponseDTO;
import com.ecourtier.domain.ia.dto.ConversationHistoryResponseDTO;
import com.ecourtier.domain.ia.dto.MessageIaResponseDTO;
import com.ecourtier.domain.ia.dto.MessageUserRequestDTO;
import com.ecourtier.domain.ia.service.IAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @PostMapping
    @Operation(summary = "Démarrer une nouvelle conversation avec l'assistant IA", description = "Accessible en mode anonyme ou authentifié.")
    public ResponseEntity<ConversationCreateResponseDTO> demarrerConversation(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        ConversationCreateResponseDTO response = iaService.creerConversation(email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Envoyer un message à l'assistant IA", description = "Analyse le besoin en langage naturel, extrait les critères et retourne une shortlist d'annonces réelles en < 3s.")
    public ResponseEntity<MessageIaResponseDTO> envoyerMessage(
            @PathVariable UUID id,
            @Valid @RequestBody MessageUserRequestDTO request,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        MessageIaResponseDTO response = iaService.envoyerMessage(id, request, email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir l'historique complet d'une conversation IA", description = "Retourne la liste chronologique des messages et des critères extraits.")
    public ResponseEntity<ConversationHistoryResponseDTO> obtenirHistorique(@PathVariable UUID id) {
        ConversationHistoryResponseDTO history = iaService.obtenirHistorique(id);
        return ResponseEntity.ok(history);
    }
}
