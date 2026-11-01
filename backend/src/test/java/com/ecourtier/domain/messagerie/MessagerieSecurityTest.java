package com.ecourtier.domain.messagerie;

import com.ecourtier.common.security.ConversationSecurityService;
import com.ecourtier.common.security.WebSocketAuthInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessagerieSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Autowired
    private ConversationSecurityService conversationSecurityService;

    @Test
    @DisplayName("REST GET /api/v1/conversations/{id}/messages par un tiers non participant — Rejet 403 Forbidden")
    @WithMockUser(username = "tiers@test.sn", roles = {"LOCATAIRE"})
    void getMessages_NonParticipant_Returns403() throws Exception {
        UUID randomConversationId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", randomConversationId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("STOMP SUBSCRIBE frame par un tiers non participant — Rejet exception accès refusé")
    void stompSubscribe_NonParticipant_Rejected() {
        UUID conversationId = UUID.randomUUID();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + conversationId);
        accessor.setUser(new UsernamePasswordAuthenticationToken("intrus@test.sn", null));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accès refusé : vous n'êtes pas participant de cette conversation");
    }
}
