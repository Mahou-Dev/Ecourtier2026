package com.ecourtier.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation de débit (rate limiting) dédiée à l'endpoint POST /api/v1/auth/rafraichir.
 * Empêche le brute-force ou rejeu automatisé massif tout en évitant tout DoS global lorsque
 * le cache borné atteint sa capacité maximale (éviction des entrées expirées puis LRU basse pression).
 */
@Service
@Slf4j
public class RefreshRateLimiterService {

    static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final long CLEANUP_THROTTLE_MS = 60_000L;
    private static final String RATE_LIMIT_MSG =
            "Trop de demandes de renouvellement de session. Veuillez réessayer ultérieurement.";

    @Value("${ecourtier.auth.refresh-rate-limit.max-attempts:20}")
    private int maxAttempts = 20;

    @Value("${ecourtier.auth.refresh-rate-limit.window-ms:60000}")
    private long windowMs = 60_000L;

    @Value("${ecourtier.auth.refresh-rate-limit.max-entries:10000}")
    private int maxEntries = DEFAULT_MAX_ENTRIES;

    private final ConcurrentHashMap<String, BoundedRateLimiterSupport.AttemptWindow> attemptsMap =
            new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();
    private final Clock clock;
    private volatile long lastCleanupTime = 0L;

    public RefreshRateLimiterService() {
        this(Clock.systemUTC());
    }

    @Autowired
    public RefreshRateLimiterService(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    RefreshRateLimiterService(Clock clock, int maxAttempts, long windowMs, int maxEntries) {
        this(clock);
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
        this.maxEntries = maxEntries;
    }

    public void checkAndRecordAttempt(String clientIp) {
        String key = BoundedRateLimiterSupport.normalizeIp(clientIp);
        long now = clock.millis();

        if ((now - lastCleanupTime) > CLEANUP_THROTTLE_MS || attemptsMap.size() >= maxEntries) {
            triggerPeriodicCleanup(now);
        }

        BoundedRateLimiterSupport.ensureCapacity(
                attemptsMap, key, now, windowMs, maxEntries, maxAttempts, evictionLock);

        BoundedRateLimiterSupport.recordAttempt(
                attemptsMap, key, now, windowMs, maxAttempts, RATE_LIMIT_MSG);
    }

    public void resetAttempts(String clientIp) {
        String key = BoundedRateLimiterSupport.normalizeIp(clientIp);
        if (!"unknown".equals(key)) {
            attemptsMap.remove(key);
        }
    }

    public void evictExpiredEntries() {
        BoundedRateLimiterSupport.evictExpired(attemptsMap, clock.millis(), windowMs);
    }

    public int getTrackedEntriesCount() {
        return attemptsMap.size();
    }

    private void triggerPeriodicCleanup(long now) {
        if ((now - lastCleanupTime) > CLEANUP_THROTTLE_MS) {
            synchronized (evictionLock) {
                if ((now - lastCleanupTime) > CLEANUP_THROTTLE_MS) {
                    lastCleanupTime = now;
                    BoundedRateLimiterSupport.evictExpired(attemptsMap, now, windowMs);
                }
            }
        }
    }
}
