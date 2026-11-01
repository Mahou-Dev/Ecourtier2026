package com.ecourtier.domain.messagerie.controller;

import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageSendDTO;
import com.ecourtier.domain.messagerie.service.MessagerieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MessagerieStompController {

    private final MessagerieService messagerieService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/conversations/{id}/envoyer")
    public void envoyerMessage(
            @DestinationVariable("id") UUID conversationId,
            @Payload MessageSendDTO payload,
            Principal principal
    ) {
        MessageResponseDTO message = messagerieService.envoyerMessage(
                conversationId,
                payload != null ? payload.getContenu() : null,
                principal != null ? principal.getName() : null
        );

        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, message);
    }
}
