package com.ecourtier.domain.messagerie.controller;

import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.service.MessagerieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Messagerie Directe", description = "Conversations privées entre locataire/acheteur et propriétaire/agent autour d'une annonce")
@SecurityRequirement(name = "bearerAuth")
public class MessagerieRestController {

    private final MessagerieService messagerieService;

    @PostMapping("/annonces/{id}/conversations")
    @Operation(
            summary = "Initier ou récupérer une conversation sur une annonce",
            description = "Ouvre un fil de discussion privé entre l'utilisateur connecté et l'auteur de l'annonce (ou retourne la conversation existante)."
    )
    public ResponseEntity<ConversationResponseDTO> initierConversation(
            @PathVariable("id") UUID annonceId,
            Authentication authentication
    ) {
        ConversationResponseDTO conversation = messagerieService.initierConversation(annonceId, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @GetMapping("/conversations/mes-conversations")
    @Operation(
            summary = "Lister mes conversations actives",
            description = "Retourne la liste des conversations auxquelles participe l'utilisateur connecté, triées par dernier message."
    )
    public ResponseEntity<List<ConversationResponseDTO>> listerMesConversations(Authentication authentication) {
        List<ConversationResponseDTO> conversations = messagerieService.listerMesConversations(authentication.getName());
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/conversations/{id}")
    @Operation(
            summary = "Consulter les métadonnées d'une conversation",
            description = "Retourne les détails d'une conversation spécifique pour l'un de ses participants."
    )
    public ResponseEntity<ConversationResponseDTO> obtenirConversation(
            @PathVariable("id") UUID conversationId,
            Authentication authentication
    ) {
        ConversationResponseDTO conversation = messagerieService.obtenirConversation(conversationId, authentication.getName());
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(
            summary = "Lister les messages d'une conversation (paginé)",
            description = "Retourne l'historique paginé des messages échangés dans la conversation (accès réservé aux participants)."
    )
    public ResponseEntity<Page<MessageResponseDTO>> listerMessagesPaging(
            @PathVariable("id") UUID conversationId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication
    ) {
        Page<MessageResponseDTO> page = messagerieService.listerMessages(conversationId, pageable, authentication.getName());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/conversations/{id}/messages/tous")
    @Operation(
            summary = "Récupérer tous les messages d'une conversation",
            description = "Retourne la liste chronologique complète des messages de la conversation pour affichage dans le tiroir de messagerie."
    )
    public ResponseEntity<List<MessageResponseDTO>> listerTousLesMessages(
            @PathVariable("id") UUID conversationId,
            Authentication authentication
    ) {
        List<MessageResponseDTO> list = messagerieService.listerTousLesMessages(conversationId, authentication.getName());
        return ResponseEntity.ok(list);
    }

    @PutMapping("/conversations/{id}/marquer-lu")
    @Operation(
            summary = "Marquer les messages reçus comme lus",
            description = "Marque comme lus tous les messages non lus adressés à l'utilisateur connecté dans cette conversation."
    )
    public ResponseEntity<Void> marquerCommeLu(
            @PathVariable("id") UUID conversationId,
            Authentication authentication
    ) {
        messagerieService.marquerCommeLu(conversationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/non-lus/count")
    @Operation(
            summary = "Compter le total des messages non lus",
            description = "Retourne le nombre total de messages non lus reçus par l'utilisateur connecté sur l'ensemble de ses conversations."
    )
    public ResponseEntity<Map<String, Long>> compterMessagesNonLus(Authentication authentication) {
        long count = messagerieService.compterMessagesNonLus(authentication.getName());
        return ResponseEntity.ok(Map.of("totalNonLus", count));
    }
}
