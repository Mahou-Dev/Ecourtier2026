package com.ecourtier.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service de limitation du taux de tentatives de connexion échouées.
 * 
 * - Max 5 tentatives consécutives dans une fenêtre glissante de 15 minutes.
 * - Nettoyage efficace avec éviction paresseuse O(1) et balayage périodique borné (toutes les 60s).
 * - Borne maximale de capacité à 10 000 entrées pour éviter la saturation mémoire sous attaque de déni de service.
 */
@Service
@Slf4j
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_MS = 15 * 60 * 1000L; // 15 minutes window
    private static final int MAX_ENTRIES = 10_000;
    private static final long CLEANUP_THROTTLE_MS = 60 * 1000L; // Balayage périodique au plus toutes les 60s

    private static class AttemptWindow {
        final long windowStart;
        final AtomicInteger attempts;

        AttemptWindow(long windowStart) {
            this.windowStart = windowStart;
            this.attempts = new AtomicInteger(1);
        }
    }

    private final Map<String, AttemptWindow> attemptsMap = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile long lastCleanupTime = 0;

    public LoginRateLimiterService() {
        this(Clock.systemUTC());
    }

    @Autowired
    public LoginRateLimiterService(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void checkAndRecordAttempt(String ip, String email) {
        String key = buildKey(ip, email);
        long now = clock.millis();

        // Éviction globale périodique amortie (évite le parcours O(N) à chaque tentative)
        if (now - lastCleanupTime > CLEANUP_THROTTLE_MS || attemptsMap.size() >= MAX_ENTRIES) {
            triggerPeriodicCleanup(now);
        }

        // Si la carte est encore saturée, rejeter par mesure défensive
        if (attemptsMap.size() >= MAX_ENTRIES && !attemptsMap.containsKey(key)) {
            evictExpiredEntries(now);
            if (attemptsMap.size() >= MAX_ENTRIES) {
                log.warn("[LOGIN RATE LIMIT] Capacité mémoire maximale atteinte ({} entrées)", MAX_ENTRIES);
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Trop de tentatives sur le service d'authentification. Veuillez réessayer ultérieurement."
                );
            }
        }

        // Éviction paresseuse O(1) lors de l'accès à la clé courante
        AttemptWindow window = attemptsMap.compute(key, (k, current) -> {
            if (current == null || (now - current.windowStart) > WINDOW_MS) {
                return new AttemptWindow(now);
            } else {
                if (current.attempts.get() >= MAX_ATTEMPTS) {
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Trop de tentatives de connexion échouées. Par mesure de sécurité, veuillez réessayer dans 15 minutes."
                    );
                }
                current.attempts.incrementAndGet();
                return current;
            }
        });

        // Double check threshold
        if (window.attempts.get() > MAX_ATTEMPTS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives de connexion échouées. Par mesure de sécurité, veuillez réessayer dans 15 minutes."
            );
        }
    }

    public void resetAttempts(String ip, String email) {
        attemptsMap.remove(buildKey(ip, email));
    }

    public void evictExpiredEntries() {
        evictExpiredEntries(clock.millis());
    }

    private synchronized void triggerPeriodicCleanup(long now) {
        if (now - lastCleanupTime > CLEANUP_THROTTLE_MS || attemptsMap.size() >= MAX_ENTRIES) {
            evictExpiredEntries(now);
            lastCleanupTime = now;
        }
    }

    private void evictExpiredEntries(long now) {
        attemptsMap.entrySet().removeIf(e -> (now - e.getValue().windowStart) > WINDOW_MS);
    }

    public int getTrackedEntriesCount() {
        return attemptsMap.size();
    }

    private String buildKey(String ip, String email) {
        return (ip != null ? ip.trim() : "unknown") + ":" + (email != null ? email.trim().toLowerCase() : "unknown");
    }
}
