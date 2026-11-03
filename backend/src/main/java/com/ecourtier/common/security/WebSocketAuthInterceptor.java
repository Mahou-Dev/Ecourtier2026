package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UtilisateurRepository utilisateurRepository;
    private final ConversationSecurityService conversationSecurityService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                List<String> authorization = accessor.getNativeHeader("Authorization");
                if (authorization == null || authorization.isEmpty()) {
                    authorization = accessor.getNativeHeader("authorization");
                }
                if (authorization != null && !authorization.isEmpty()) {
                    String authHeader = authorization.get(0);
                    String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
                    if (jwtService.isTokenValid(token)) {
                        String email = jwtService.extractEmail(token);
                        UtilisateurEntity utilisateur = utilisateurRepository.findByEmail(email).orElse(null);
                        if (utilisateur == null || !utilisateur.isActif()) {
                            throw new IllegalArgumentException("Le compte utilisateur est désactivé");
                        }
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                        accessor.setUser(auth);
                    } else {
                        throw new IllegalArgumentException("Token JWT STOMP invalide ou expiré");
                    }
                } else {
                    throw new IllegalArgumentException("Header Authorization manquant dans le frame STOMP CONNECT");
                }
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (destination != null && destination.startsWith("/topic/conversations/")) {
                    String conversationIdStr = destination.substring("/topic/conversations/".length());
                    try {
                        UUID conversationId = UUID.fromString(conversationIdStr);
                        Principal principal = accessor.getUser();
                        if (principal == null || !conversationSecurityService.isParticipant(conversationId, principal.getName())) {
                            throw new IllegalArgumentException("Accès refusé : vous n'êtes pas participant de cette conversation");
                        }
                    } catch (IllegalArgumentException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Destination ou ID de conversation invalide");
                    }
                }
            } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                if (destination != null && destination.startsWith("/app/conversations/")) {
                    String[] parts = destination.split("/");
                    if (parts.length >= 4) {
                        try {
                            UUID conversationId = UUID.fromString(parts[3]);
                            Principal principal = accessor.getUser();
                            if (principal == null || !conversationSecurityService.isParticipant(conversationId, principal.getName())) {
                                throw new IllegalArgumentException("Accès refusé : vous n'êtes pas participant de cette conversation");
                            }
                        } catch (IllegalArgumentException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Destination ou ID de conversation invalide");
                        }
                    }
                }
            }
        }
        return message;
    }
}
