package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.RefreshTokenRepository;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre thread-safe des sessions WebSocket actives associées aux utilisateurs authentifiés par STOMP.
 *
 * - Ferme immédiatement les sessions lors de la désactivation, déconnexion, changement de mot de passe ou compromission d'une famille de jetons.
 * - Suit l'expiration JWT (exp), la version de jeton (tokenVersion) et l'identifiant de famille de session (familyId) de chaque session.
 * - Portée : broker STOMP en mémoire (mono-JVM).
 */
@Component
@Slf4j
public class WebSocketSessionRegistry {

    public record SessionMetadata(String email, Long jwtExpiresAtMillis, Long tokenVersion, UUID familyId) {
        public SessionMetadata(String email, Long jwtExpiresAtMillis, Long tokenVersion) {
            this(email, jwtExpiresAtMillis, tokenVersion, null);
        }
    }

    private final ConcurrentHashMap<String, WebSocketSession> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionIdsByEmail = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> emailBySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionMetadata> metadataBySessionId = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private UtilisateurRepository utilisateurRepository;

    @Autowired(required = false)
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired(required = false)
    private Clock clock = Clock.system(ZoneId.of("Africa/Dakar"));

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public void registerSession(WebSocketSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        sessionsById.put(session.getId(), session);
    }

    public void bindUser(String sessionId, String email) {
        bindUser(sessionId, email, null, null, null);
    }

    public void bindUser(String sessionId, String email, Long jwtExpiresAtMillis, Long tokenVersion) {
        bindUser(sessionId, email, jwtExpiresAtMillis, tokenVersion, null);
    }

    public void bindUser(String sessionId, String email, Long jwtExpiresAtMillis, Long tokenVersion, UUID familyId) {
        if (sessionId == null || sessionId.isBlank() || email == null || email.isBlank()) {
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        String previousEmail = emailBySessionId.put(sessionId, normalizedEmail);
        if (previousEmail != null && !previousEmail.equals(normalizedEmail)) {
            removeFromEmailSet(previousEmail, sessionId);
        }
        sessionIdsByEmail
                .computeIfAbsent(normalizedEmail, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        metadataBySessionId.put(sessionId, new SessionMetadata(normalizedEmail, jwtExpiresAtMillis, tokenVersion, familyId));
    }

    public SessionMetadata getSessionMetadata(String sessionId) {
        return sessionId != null ? metadataBySessionId.get(sessionId) : null;
    }

    public void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionsById.remove(sessionId);
        metadataBySessionId.remove(sessionId);
        String email = emailBySessionId.remove(sessionId);
        if (email != null) {
            removeFromEmailSet(email, sessionId);
        }
    }

    public boolean closeSession(String sessionId, CloseStatus status) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        WebSocketSession session = sessionsById.get(sessionId);
        removeSession(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(status != null ? status : CloseStatus.POLICY_VIOLATION);
                return true;
            } catch (Exception ex) {
                log.warn("[WS] Échec lors de la fermeture forcée de la session {}: {}", sessionId, ex.getMessage());
            }
        }
        return false;
    }

    public int disconnectUser(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        String normalizedEmail = normalizeEmail(email);
        Set<String> sessionIds = sessionIdsByEmail.remove(normalizedEmail);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0;
        }

        int closedCount = 0;
        for (String sessionId : Set.copyOf(sessionIds)) {
            if (closeSession(sessionId, CloseStatus.POLICY_VIOLATION)) {
                closedCount++;
            }
        }
        return closedCount;
    }

    public int disconnectFamily(UUID familyId) {
        if (familyId == null) {
            return 0;
        }
        int closedCount = 0;
        for (Map.Entry<String, SessionMetadata> entry : Set.copyOf(metadataBySessionId.entrySet())) {
            SessionMetadata metadata = entry.getValue();
            if (metadata != null && familyId.equals(metadata.familyId())) {
                if (closeSession(entry.getKey(), CloseStatus.POLICY_VIOLATION)) {
                    closedCount++;
                }
            }
        }
        return closedCount;
    }

    public boolean isSessionValidAndActive(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        SessionMetadata metadata = metadataBySessionId.get(sessionId);
        if (metadata == null) {
            return false;
        }
        long now = effectiveClock().millis();
        if (metadata.jwtExpiresAtMillis() != null && now > metadata.jwtExpiresAtMillis()) {
            closeSession(sessionId, CloseStatus.POLICY_VIOLATION);
            return false;
        }
        if (utilisateurRepository != null && metadata.email() != null) {
            UtilisateurEntity utilisateur = utilisateurRepository.findByEmail(metadata.email()).orElse(null);
            long currentVersion = utilisateur != null && utilisateur.getTokenVersion() != null
                    ? utilisateur.getTokenVersion()
                    : 0L;
            boolean familyRevoked = utilisateur != null
                    && metadata.familyId() != null
                    && refreshTokenRepository != null
                    && !refreshTokenRepository.isFamilyActive(
                            metadata.familyId(),
                            utilisateur.getId(),
                            LocalDateTime.now(effectiveClock())
                    );
            if (utilisateur == null || !utilisateur.isActif()
                    || metadata.tokenVersion() == null
                    || metadata.tokenVersion().longValue() != currentVersion
                    || familyRevoked) {
                closeSession(sessionId, CloseStatus.POLICY_VIOLATION);
                return false;
            }
        }
        return true;
    }

    /**
     * Balayage périodique des sessions WebSocket dont le JWT est expiré ou dont la version de jeton / la famille / le statut
     * ne sont plus valides, même si le client reste purement passif (aucun message entrant/sortant).
     */
    @Scheduled(fixedDelayString = "${ecourtier.websocket.session-sweep-ms:5000}")
    public int sweepExpiredOrRevokedSessions() {
        long now = effectiveClock().millis();
        int closedCount = 0;

        for (Map.Entry<String, WebSocketSession> entry : Set.copyOf(sessionsById.entrySet())) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                removeSession(sessionId);
                continue;
            }
            SessionMetadata metadata = metadataBySessionId.get(sessionId);
            if (metadata == null) {
                continue;
            }
            boolean expired = metadata.jwtExpiresAtMillis() != null && now > metadata.jwtExpiresAtMillis();
            if (expired) {
                if (closeSession(sessionId, CloseStatus.POLICY_VIOLATION)) {
                    closedCount++;
                }
                continue;
            }
            if (utilisateurRepository != null && metadata.email() != null) {
                UtilisateurEntity user = utilisateurRepository.findByEmail(metadata.email()).orElse(null);
                long currentVersion = user != null && user.getTokenVersion() != null ? user.getTokenVersion() : 0L;
                boolean familyRevoked = user != null
                        && metadata.familyId() != null
                        && refreshTokenRepository != null
                        && !refreshTokenRepository.isFamilyActive(
                                metadata.familyId(),
                                user.getId(),
                                LocalDateTime.now(effectiveClock())
                        );
                if (user == null || !user.isActif()
                        || metadata.tokenVersion() == null
                        || metadata.tokenVersion().longValue() != currentVersion
                        || familyRevoked) {
                    if (closeSession(sessionId, CloseStatus.POLICY_VIOLATION)) {
                        closedCount++;
                    }
                }
            }
        }
        return closedCount;
    }

    public int getActiveSessionCount(String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        Set<String> sessionIds = sessionIdsByEmail.getOrDefault(normalizeEmail(email), Collections.emptySet());
        int openCount = 0;
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessionsById.get(sessionId);
            if (session != null && session.isOpen()) {
                openCount++;
            }
        }
        return openCount;
    }

    public int getRegisteredSessionCount() {
        return sessionsById.size();
    }

    java.util.List<String> getActiveSessionIds(String email) {
        if (email == null || email.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(
                sessionIdsByEmail.getOrDefault(normalizeEmail(email), Collections.emptySet())
        );
    }

    private Clock effectiveClock() {
        return clock != null ? clock : Clock.system(ZoneId.of("Africa/Dakar"));
    }

    private void removeFromEmailSet(String email, String sessionId) {
        sessionIdsByEmail.computeIfPresent(email, (ignored, set) -> {
            set.remove(sessionId);
            return set.isEmpty() ? null : set;
        });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
