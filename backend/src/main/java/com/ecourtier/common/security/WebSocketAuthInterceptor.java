package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    static final String ACCES_REFUSE_PARTICIPANT = "Accès refusé : vous n'êtes pas participant de cette conversation";

    private static final Pattern SUBSCRIBE_CONVERSATION = Pattern.compile(
            "^/topic/conversations/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");
    private static final Pattern SEND_CONVERSATION = Pattern.compile(
            "^/app/conversations/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/envoyer$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UtilisateurRepository utilisateurRepository;
    private final ConversationSecurityService conversationSecurityService;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired(required = false)
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired(required = false)
    private Clock clock = Clock.system(ZoneId.of("Africa/Dakar"));

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.system(ZoneId.of("Africa/Dakar"));
    }

    private long nowMillis() {
        return clock != null ? clock.millis() : System.currentTimeMillis();
    }

    private LocalDateTime nowLocalDateTime() {
        return LocalDateTime.now(clock != null ? clock : Clock.system(ZoneId.of("Africa/Dakar")));
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authentifierConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            autoriserDestination(accessor, SUBSCRIBE_CONVERSATION, "Abonnement STOMP non autorisé sur cette destination");
        } else if (StompCommand.SEND.equals(command)) {
            autoriserDestination(accessor, SEND_CONVERSATION, "Envoi STOMP non autorisé sur cette destination");
        }
        return message;
    }

    /**
     * Intercepteur du canal sortant vers les clients WebSocket : bloque immédiatement tout message
     * vers une session expirée, désactivée ou révoquée (logout / changement de mot de passe) et ferme la socket.
     */
    public ChannelInterceptor outboundChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
                if (accessor == null || !SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
                    return message;
                }
                String sessionId = accessor.getSessionId();
                if (sessionId == null) {
                    return message;
                }
                if (!webSocketSessionRegistry.isSessionValidAndActive(sessionId)) {
                    return null;
                }
                return message;
            }
        };
    }

    private void authentifierConnect(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            authorization = accessor.getNativeHeader("authorization");
        }
        if (authorization == null || authorization.isEmpty()) {
            throw new IllegalArgumentException("Header Authorization manquant dans le frame STOMP CONNECT");
        }
        String authHeader = authorization.get(0);
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!jwtService.isAccessTokenValid(token)) {
            throw new IllegalArgumentException("Token JWT STOMP invalide, expiré ou type non autorisé");
        }
        String email = jwtService.extractEmail(token);
        Long tokenVersionClaim = jwtService.extractTokenVersion(token);
        UUID familyId = jwtService.extractFamilyId(token);
        UtilisateurEntity user = loadActiveUser(email);
        if (user == null) {
            throw new IllegalArgumentException("Le compte utilisateur est désactivé");
        }
        long currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0L;
        boolean familyRevoked = familyId != null
                && refreshTokenRepository != null
                && !refreshTokenRepository.isFamilyActive(familyId, user.getId(), nowLocalDateTime());
        if (tokenVersionClaim == null || tokenVersionClaim.longValue() != currentVersion || familyRevoked) {
            throw new IllegalArgumentException("Token JWT STOMP révoqué : veuillez vous reconnecter");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        accessor.setUser(auth);

        java.util.Date expiration = jwtService.extractExpiration(token);
        Long expiresAtMillis = expiration != null ? expiration.getTime() : null;
        if (accessor.getSessionAttributes() != null) {
            if (expiresAtMillis != null) {
                accessor.getSessionAttributes().put("jwtExpiresAtMillis", expiresAtMillis);
            }
            accessor.getSessionAttributes().put("tokenVersion", tokenVersionClaim);
            if (familyId != null) {
                accessor.getSessionAttributes().put("familyId", familyId);
            }
        }
        webSocketSessionRegistry.bindUser(accessor.getSessionId(), email, expiresAtMillis, tokenVersionClaim, familyId);
    }

    private void autoriserDestination(StompHeaderAccessor accessor, Pattern allowedPattern, String refusMessage) {
        String destination = accessor.getDestination();
        Matcher matcher = destination != null ? allowedPattern.matcher(destination) : null;
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException(refusMessage);
        }
        UUID conversationId;
        try {
            conversationId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Destination ou ID de conversation invalide");
        }
        Principal principal = accessor.getUser();
        if (principal == null || !conversationSecurityService.isParticipant(conversationId, principal.getName())) {
            throw new IllegalArgumentException(ACCES_REFUSE_PARTICIPANT);
        }
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs != null && sessionAttrs.get("jwtExpiresAtMillis") instanceof Long expiresAtMillis
                && nowMillis() > expiresAtMillis) {
            webSocketSessionRegistry.closeSession(accessor.getSessionId(), CloseStatus.POLICY_VIOLATION);
            throw new IllegalArgumentException("Session WebSocket expirée : veuillez renouveler votre token");
        }
        UtilisateurEntity user = loadActiveUser(principal.getName());
        if (user == null) {
            webSocketSessionRegistry.closeSession(accessor.getSessionId(), CloseStatus.POLICY_VIOLATION);
            throw new IllegalArgumentException("Le compte utilisateur est désactivé");
        }
        Long sessionTokenVersion = sessionAttrs != null && sessionAttrs.get("tokenVersion") instanceof Long v ? v : null;
        UUID sessionFamilyId = sessionAttrs != null && sessionAttrs.get("familyId") instanceof UUID f ? f : null;
        long currentVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0L;
        boolean familyRevoked = sessionFamilyId != null
                && refreshTokenRepository != null
                && !refreshTokenRepository.isFamilyActive(sessionFamilyId, user.getId(), nowLocalDateTime());
        if (sessionTokenVersion == null || sessionTokenVersion.longValue() != currentVersion || familyRevoked) {
            webSocketSessionRegistry.closeSession(accessor.getSessionId(), CloseStatus.POLICY_VIOLATION);
            throw new IllegalArgumentException("Session WebSocket révoquée : veuillez vous reconnecter");
        }
    }

    private UtilisateurEntity loadActiveUser(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        UtilisateurEntity utilisateur = utilisateurRepository.findByEmail(email).orElse(null);
        return (utilisateur != null && utilisateur.isActif()) ? utilisateur : null;
    }
}
