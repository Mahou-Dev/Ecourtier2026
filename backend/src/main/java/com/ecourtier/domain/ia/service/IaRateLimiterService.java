package com.ecourtier.domain.ia.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service de limitation de débit (Rate Limiting) et de protection anti-abus pour les endpoints de l'IA.
 * 
 * Limites par défaut :
 * - Utilisateur anonyme : 10 requêtes / minute
 * - Utilisateur authentifié : 30 requêtes / minute
 * - Capacité maximale de la structure mémoire : 5 000 clés suivies avec éviction bornée
 * 
 * Portée : Limitation locale à l'instance courante.
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

    private static class RequestWindow {
        final long windowStart;
        final AtomicInteger count;

        RequestWindow(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }
    }

    private final Map<String, RequestWindow> rateLimitMap = new ConcurrentHashMap<>();
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
        triggerPeriodicCleanup(now);

        // Si le cache mémoire sature malgré l'éviction, rejeter par précaution
        if (rateLimitMap.size() >= MAX_TRACKED_KEYS && !rateLimitMap.containsKey(clientKey)) {
            evictExpiredEntries(now);
            if (rateLimitMap.size() >= MAX_TRACKED_KEYS) {
                log.warn("[IA RATE LIMIT] Capacité mémoire maximale atteinte ({} clés)", MAX_TRACKED_KEYS);
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Le service d'assistance IA est temporairement saturé. Veuillez réessayer dans quelques instants."
                );
            }
        }

        RequestWindow window = rateLimitMap.compute(clientKey, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > WINDOW_DURATION_MS) {
                return new RequestWindow(now);
            } else {
                if (existing.count.get() >= maxAllowed) {
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            String.format("Limite de requêtes atteinte pour l'assistant IA (%d req/min). Veuillez patienter avant de renvoyer un message.", maxAllowed)
                    );
                }
                existing.count.incrementAndGet();
                return existing;
            }
        });

        if (window.count.get() > maxAllowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    String.format("Limite de requêtes atteinte pour l'assistant IA (%d req/min). Veuillez patienter avant de renvoyer un message.", maxAllowed)
            );
        }
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

        // Pour les utilisateurs anonymes, utiliser l'adresse IP distante réelle
        String remoteAddr = request != null ? request.getRemoteAddr() : "127.0.0.1";
        if (remoteAddr == null || remoteAddr.isBlank()) {
            remoteAddr = "unknown-ip";
        }
        return "anon:" + remoteAddr.trim();
    }

    private void triggerPeriodicCleanup(long now) {
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                    evictExpiredEntries(now);
                    lastCleanupTime = now;
                }
            }
        }
    }

    private void evictExpiredEntries(long now) {
        rateLimitMap.entrySet().removeIf(entry -> (now - entry.getValue().windowStart) > WINDOW_DURATION_MS);
    }
}
