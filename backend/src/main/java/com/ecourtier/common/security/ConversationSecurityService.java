package com.ecourtier.common.security;

import com.ecourtier.domain.messagerie.repository.ConversationChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("conversationSecurityService")
@RequiredArgsConstructor
public class ConversationSecurityService {

    private final ConversationChatRepository conversationChatRepository;

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return isParticipant(conversationId, authentication.getName());
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, String userEmail) {
        if (conversationId == null || userEmail == null || userEmail.isBlank()) {
            return false;
        }
        return conversationChatRepository.isParticipant(conversationId, userEmail);
    }
}
