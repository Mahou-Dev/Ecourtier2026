package com.ecourtier.domain.ia.service;

import com.ecourtier.common.security.BoundedRateLimiterSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de limitation de débit (Rate Limiting) et de protection anti-abus pour les endpoints de l'IA.
 *
 * Limites par défaut :
 * - Utilisateur anonyme : 10 requêtes / minute
 * - Utilisateur authentifié : 30 requêtes / minute
 * - Capacité maximale de la structure mémoire : 5 000 clés suivies avec éviction bornée O(1) amortie
 *   (préserve les clients déjà bloqués et évite tout DoS global sur nouveaux utilisateurs légitimes).
 */
@Service
@Slf4j
public class IaRateLimiterService {

    private static final int DEFAULT_ANONYMOUS_LIMIT = 10;
    private static final int DEFAULT_AUTHENTICATED_LIMIT = 30;
    private static final long WINDOW_DURATION_MS = 60 * 1000L; // 1 minute
    private static final int MAX_TRACKED_KEYS = 5_000;
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000L;

    @Value("${ecourtier.ia.rate-limit.anonymous:10}")
    private int anonymousLimit = DEFAULT_ANONYMOUS_LIMIT;

    @Value("${ecourtier.ia.rate-limit.authenticated:30}")
    private int authenticatedLimit = DEFAULT_AUTHENTICATED_LIMIT;

    private final ConcurrentHashMap<String, BoundedRateLimiterSupport.AttemptWindow> rateLimitMap =
            new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();
    private final Clock clock;
    private volatile long lastCleanupTime = 0;

    public IaRateLimiterService() {
        this(Clock.systemUTC());
    }

    @Autowired
    public IaRateLimiterService(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Vérifie le quota de requêtes pour la requête HTTP et l'identité courante.
     * Déclenche une exception HTTP 429 en cas de dépassement.
     */
    public void checkRateLimit(HttpServletRequest request, Authentication authentication) {
        boolean isAuthenticated = isAuthenticatedUser(authentication);
        String clientKey = resolveClientIdentifier(request, authentication, isAuthenticated);
        int maxAllowed = isAuthenticated ? authenticatedLimit : anonymousLimit;
        long now = clock.millis();

        if ((now - lastCleanupTime) > CLEANUP_INTERVAL_MS || rateLimitMap.size() >= MAX_TRACKED_KEYS) {
            triggerPeriodicCleanup(now);
        }

        BoundedRateLimiterSupport.ensureCapacity(
                rateLimitMap, clientKey, now, WINDOW_DURATION_MS, MAX_TRACKED_KEYS, maxAllowed, evictionLock);

        String message = String.format(
                "Limite de requêtes atteinte pour l'assistant IA (%d req/min). Veuillez patienter avant de renvoyer un message.",
                maxAllowed
        );
        BoundedRateLimiterSupport.recordAttempt(
                rateLimitMap, clientKey, now, WINDOW_DURATION_MS, maxAllowed, message);
    }

    /**
     * Réinitialise les compteurs pour un client (utile pour les tests déterministes).
     */
    public void reset(String clientKey) {
        if (clientKey != null) {
            rateLimitMap.remove(clientKey);
        }
    }

    public void clear() {
        rateLimitMap.clear();
    }

    public int getTrackedEntriesCount() {
        return rateLimitMap.size();
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !authentication.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(authentication.getName().trim());
    }

    private String resolveClientIdentifier(HttpServletRequest request, Authentication authentication, boolean isAuthenticated) {
        if (isAuthenticated) {
            return "auth:" + authentication.getName().trim().toLowerCase();
        }

        // Pour les utilisateurs anonymes, utiliser l'adresse IP distante normalisée
        String remoteAddr = request != null ? request.getRemoteAddr() : "127.0.0.1";
        return "anon:" + BoundedRateLimiterSupport.normalizeIp(remoteAddr);
    }

    private void triggerPeriodicCleanup(long now) {
        if ((now - lastCleanupTime) > CLEANUP_INTERVAL_MS) {
            synchronized (evictionLock) {
                if ((now - lastCleanupTime) > CLEANUP_INTERVAL_MS) {
                    BoundedRateLimiterSupport.evictExpired(rateLimitMap, now, WINDOW_DURATION_MS);
                    lastCleanupTime = now;
                }
            }
        }
    }
}
