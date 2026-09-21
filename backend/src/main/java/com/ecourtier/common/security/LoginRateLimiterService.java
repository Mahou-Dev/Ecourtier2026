package com.ecourtier.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes window

    private static class AttemptWindow {
        final long windowStart;
        final AtomicInteger attempts;

        AttemptWindow(long windowStart) {
            this.windowStart = windowStart;
            this.attempts = new AtomicInteger(1);
        }
    }

    private final Map<String, AttemptWindow> attemptsMap = new ConcurrentHashMap<>();

    public void checkAndRecordAttempt(String ip, String email) {
        String key = (ip != null ? ip.trim() : "unknown") + ":" + (email != null ? email.trim().toLowerCase() : "unknown");
        long now = Instant.now().toEpochMilli();

        // Evict expired entries if map grows
        if (attemptsMap.size() > 5000) {
            attemptsMap.entrySet().removeIf(e -> (now - e.getValue().windowStart) > WINDOW_MS);
        }

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
        String key = (ip != null ? ip.trim() : "unknown") + ":" + (email != null ? email.trim().toLowerCase() : "unknown");
        attemptsMap.remove(key);
    }
}
