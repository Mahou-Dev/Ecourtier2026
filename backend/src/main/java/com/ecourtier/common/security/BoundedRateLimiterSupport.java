package com.ecourtier.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilitaire partagé pour la normalisation des adresses IP clientes et l'éviction bornée
 * des fenêtres de limitation de débit (évite tout DoS global lorsque la capacité mémoire est atteinte).
 */
public final class BoundedRateLimiterSupport {

    public static final class AttemptWindow {
        volatile long windowStart;
        volatile long lastAccess;
        public final AtomicInteger attempts;

        public AttemptWindow(long now) {
            this.windowStart = now;
            this.lastAccess = now;
            this.attempts = new AtomicInteger(1);
        }
    }

    private BoundedRateLimiterSupport() {
    }

    public static String normalizeIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return "unknown";
        }
        String candidate = rawIp.trim();
        // Prendre uniquement le premier segment si une liste comma-separated arrivait accidentellement
        int commaIdx = candidate.indexOf(',');
        if (commaIdx > 0) {
            candidate = candidate.substring(0, commaIdx).trim();
        }
        if (candidate.startsWith("[")) {
            int closingBracket = candidate.indexOf(']');
            if (closingBracket > 1) {
                candidate = candidate.substring(1, closingBracket);
            }
        } else {
            // IPv4 avec port (un seul ':' sans autre ':')
            int firstColon = candidate.indexOf(':');
            if (firstColon > 0 && candidate.indexOf(':', firstColon + 1) < 0) {
                candidate = candidate.substring(0, firstColon);
            }
        }
        if (candidate.isBlank()) {
            return "unknown";
        }
        // Éviter toute résolution DNS réseau : valider uniquement les littéraux IPv4 / IPv6
        if (isIpLiteral(candidate)) {
            try {
                return InetAddress.getByName(candidate).getHostAddress();
            } catch (Exception ignored) {
                return candidate.toLowerCase();
            }
        }
        return candidate.toLowerCase();
    }

    private static boolean isIpLiteral(String value) {
        boolean hasColon = false;
        boolean hasDot = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ':') {
                hasColon = true;
            } else if (c == '.') {
                hasDot = true;
            } else if (Character.digit(c, 16) < 0) {
                return false;
            }
        }
        return hasColon || hasDot;
    }

    public static int evictExpired(Map<String, AttemptWindow> map, long now, long windowMs) {
        int removed = 0;
        var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AttemptWindow> entry = iterator.next();
            if ((now - entry.getValue().windowStart) > windowMs) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Libère de la capacité de manière bornée lorsque maxEntries est atteint malgré la purge des entrées expirées.
     * Préserve en priorité les IPs déjà bloquées (attempts >= maxAttempts) afin qu'un flood d'IPs jetables
     * ne puisse ni contourner un blocage actif ni provoquer un DoS global sur de nouveaux clients légitimes.
     */
    public static void ensureCapacity(
            ConcurrentHashMap<String, AttemptWindow> map,
            String incomingKey,
            long now,
            long windowMs,
            int maxEntries,
            int maxAttempts,
            Object lock
    ) {
        if (map.size() < maxEntries || map.containsKey(incomingKey)) {
            return;
        }
        synchronized (lock) {
            if (map.size() < maxEntries || map.containsKey(incomingKey)) {
                return;
            }
            evictExpired(map, now, windowMs);
            if (map.size() < maxEntries) {
                return;
            }
            int overflow = (map.size() - maxEntries) + 1;
            int batchTarget = Math.max(overflow, Math.max(1, maxEntries / 10));

            List<Map.Entry<String, AttemptWindow>> candidates = map.entrySet().stream()
                    .sorted(Comparator
                            .<Map.Entry<String, AttemptWindow>>comparingInt(
                                    e -> e.getValue().attempts.get() >= maxAttempts ? 1 : 0)
                            .thenComparingInt(e -> e.getValue().attempts.get())
                            .thenComparingLong(e -> e.getValue().lastAccess))
                    .limit(batchTarget)
                    .toList();

            for (Map.Entry<String, AttemptWindow> candidate : candidates) {
                map.remove(candidate.getKey(), candidate.getValue());
            }
        }
    }

    public static void recordAttempt(
            ConcurrentHashMap<String, AttemptWindow> map,
            String key,
            long now,
            long windowMs,
            int maxAttempts,
            String limitMessage
    ) {
        AttemptWindow window = map.compute(key, (ignored, current) -> {
            if (current == null || (now - current.windowStart) > windowMs) {
                return new AttemptWindow(now);
            }
            current.lastAccess = now;
            if (current.attempts.get() >= maxAttempts) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, limitMessage);
            }
            current.attempts.incrementAndGet();
            return current;
        });

        if (window.attempts.get() > maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, limitMessage);
        }
    }
}
