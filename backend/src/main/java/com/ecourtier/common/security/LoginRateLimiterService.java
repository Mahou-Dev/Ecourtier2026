package com.ecourtier.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de limitation du taux de tentatives de connexion échouées.
 *
 * - Max 5 tentatives consécutives dans une fenêtre glissante de 15 minutes.
 * - Éviction O(1) amortie (entrées expirées puis éviction prioritaire basse pression si capacité maximale atteinte).
 */
@Service
@Slf4j
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 25;
    static final long WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_ENTRIES = 10_000;
    private static final long CLEANUP_THROTTLE_MS = 60 * 1000L;
    private static final String RATE_LIMIT_MSG =
            "Trop de tentatives de connexion échouées. Par mesure de sécurité, veuillez réessayer dans 15 minutes.";

    private final ConcurrentHashMap<String, BoundedRateLimiterSupport.AttemptWindow> attemptsMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BoundedRateLimiterSupport.AttemptWindow> ipAttemptsMap =
            new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();
    private final Clock clock;
    private volatile long lastCleanupTime = 0L;

    public LoginRateLimiterService() {
        this(Clock.systemUTC());
    }

    @Autowired
    public LoginRateLimiterService(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void checkAndRecordAttempt(String ip, String email) {
        String normalizedIp = BoundedRateLimiterSupport.normalizeIp(ip);
        String key = buildKey(normalizedIp, email);
        long now = clock.millis();

        if (now - lastCleanupTime > CLEANUP_THROTTLE_MS
                || attemptsMap.size() >= MAX_ENTRIES
                || ipAttemptsMap.size() >= MAX_ENTRIES) {
            triggerPeriodicCleanup(now);
        }

        BoundedRateLimiterSupport.ensureCapacity(
                ipAttemptsMap, normalizedIp, now, WINDOW_MS, MAX_ENTRIES, MAX_ATTEMPTS_PER_IP, evictionLock);
        BoundedRateLimiterSupport.recordAttempt(
                ipAttemptsMap, normalizedIp, now, WINDOW_MS, MAX_ATTEMPTS_PER_IP, RATE_LIMIT_MSG);

        BoundedRateLimiterSupport.ensureCapacity(
                attemptsMap, key, now, WINDOW_MS, MAX_ENTRIES, MAX_ATTEMPTS, evictionLock);
        BoundedRateLimiterSupport.recordAttempt(
                attemptsMap, key, now, WINDOW_MS, MAX_ATTEMPTS, RATE_LIMIT_MSG);
    }

    public void resetAttempts(String ip, String email) {
        String normalizedIp = BoundedRateLimiterSupport.normalizeIp(ip);
        attemptsMap.remove(buildKey(normalizedIp, email));
        ipAttemptsMap.computeIfPresent(normalizedIp, (ignored, window) -> {
            if (window.attempts.decrementAndGet() <= 0) {
                return null;
            }
            return window;
        });
    }

    public void evictExpiredEntries() {
        long now = clock.millis();
        BoundedRateLimiterSupport.evictExpired(attemptsMap, now, WINDOW_MS);
        BoundedRateLimiterSupport.evictExpired(ipAttemptsMap, now, WINDOW_MS);
    }

    private void triggerPeriodicCleanup(long now) {
        if (now - lastCleanupTime > CLEANUP_THROTTLE_MS) {
            synchronized (evictionLock) {
                if (now - lastCleanupTime > CLEANUP_THROTTLE_MS) {
                    BoundedRateLimiterSupport.evictExpired(attemptsMap, now, WINDOW_MS);
                    BoundedRateLimiterSupport.evictExpired(ipAttemptsMap, now, WINDOW_MS);
                    lastCleanupTime = now;
                }
            }
        }
    }

    public int getTrackedEntriesCount() {
        return attemptsMap.size();
    }

    private String buildKey(String ip, String email) {
        return BoundedRateLimiterSupport.normalizeIp(ip) + ":"
                + (email != null ? email.trim().toLowerCase() : "unknown");
    }
}
