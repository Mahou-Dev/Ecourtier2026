package com.ecourtier.domain.messagerie.controller;

import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.service.MessagerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessagerieRestController {

    private final MessagerieService messagerieService;

    @PostMapping("/annonces/{id}/conversations")
    public ResponseEntity<ConversationResponseDTO> initierConversation(
            @PathVariable("id") UUID annonceId,
            Authentication authentication
    ) {
        ConversationResponseDTO conversation = messagerieService.initierConversation(annonceId, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @GetMapping("/conversations/mes-conversations")
    public ResponseEntity<List<ConversationResponseDTO>> listerMesConversations(Authentication authentication) {
        List<ConversationResponseDTO> conversations = messagerieService.listerMesConversations(authentication.getName());
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationResponseDTO> obtenirConversation(
            @PathVariable("id") UUID conversationId,
            Authentication authentication
    ) {
        ConversationResponseDTO conversation = messagerieService.obtenirConversation(conversationId, authentication.getName());
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Page<MessageResponseDTO>> listerMessagesPaging(
            @PathVariable("id") UUID conversationId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication
    ) {
        Page<MessageResponseDTO> page = messagerieService.listerMessages(conversationId, pageable, authentication.getName());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/conversations/{id}/messages/tous")
    public ResponseEntity<List<MessageResponseDTO>> listerTousLesMessages(
            @PathVariable("id") UUID conversationId,
            Authentication authentication
    ) {
        List<MessageResponseDTO> list = messagerieService.listerTousLesMessages(conversationId, authentication.getName());
        return ResponseEntity.ok(list);
    }

    @PutMapping("/conversations/{id}/marquer-lu")
    public ResponseEntity<Void> marquerCommeLu(
            @PathVariable("id") UUID conversationId,
            Authentication authentication
    ) {
        messagerieService.marquerCommeLu(conversationId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/non-lus/count")
    public ResponseEntity<Map<String, Long>> compterMessagesNonLus(Authentication authentication) {
        long count = messagerieService.compterMessagesNonLus(authentication.getName());
        return ResponseEntity.ok(Map.of("totalNonLus", count));
    }
}
