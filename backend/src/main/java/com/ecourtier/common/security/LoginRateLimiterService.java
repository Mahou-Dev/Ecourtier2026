package com.ecourtier.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes window

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

        // Purge expired windows so memory does not accumulate stale entries
        evictExpiredEntries(now);

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
