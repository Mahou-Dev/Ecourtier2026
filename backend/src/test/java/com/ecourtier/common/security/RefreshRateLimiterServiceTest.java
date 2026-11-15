package com.ecourtier.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshRateLimiterServiceTest {

    private Clock mutableClock(AtomicLong millis) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis.get());
            }
        };
    }

    @Test
    @DisplayName("A2 : Au-delà de 10 000 IP distinctes actives, une nouvelle IP légitime n'est PAS bloquée (pas de DoS global)")
    void testBeyondTenThousandDistinctIpsDoesNotBlockLegitimateNewIp() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        RefreshRateLimiterService limiter = new RefreshRateLimiterService(mutableClock(now));

        // Saturation au-delà du plafond par défaut (12 500 IPs distinctes actives dans la même minute)
        for (int i = 0; i < 12_500; i++) {
            String ip = "10." + ((i >> 16) & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + (i & 0xFF);
            assertDoesNotThrow(() -> limiter.checkAndRecordAttempt(ip));
        }

        // Mémoire strictement bornée à <= 10 000 entrées
        assertTrue(limiter.getTrackedEntriesCount() <= RefreshRateLimiterService.DEFAULT_MAX_ENTRIES,
                "Le cache ne doit jamais dépasser 10 000 entrées : " + limiter.getTrackedEntriesCount());

        // Une nouvelle IP légitime doit toujours passer sans HTTP 429 global
        assertDoesNotThrow(() -> limiter.checkAndRecordAttempt("41.82.100.10"));
    }

    @Test
    @DisplayName("A2 : Un attaquant déjà bloqué ne peut pas contourner son blocage 429 en inondant de > 10 000 fausses IPs")
    void testFloodingCannotEvictActivelyBlockedIp() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        RefreshRateLimiterService limiter = new RefreshRateLimiterService(mutableClock(now));
        String attackerIp = "198.51.100.77";

        // 20 tentatives autorisées -> 21e bloquée
        for (int i = 0; i < 20; i++) {
            limiter.checkAndRecordAttempt(attackerIp);
        }
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> limiter.checkAndRecordAttempt(attackerIp));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());

        // Flood de 11 000 IPs jetables à 1 tentative chacune
        for (int i = 0; i < 11_000; i++) {
            String decoyIp = "172.16." + ((i >> 8) & 0xFF) + "." + (i & 0xFF);
            limiter.checkAndRecordAttempt(decoyIp);
        }

        // L'attaquant reste bloqué en 429 malgré le flood
        ResponseStatusException stillBlocked = assertThrows(ResponseStatusException.class,
                () -> limiter.checkAndRecordAttempt(attackerIp));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, stillBlocked.getStatusCode());

        // Une IP légitime reste autorisée
        assertDoesNotThrow(() -> limiter.checkAndRecordAttempt("41.82.200.20"));
    }

    @Test
    @DisplayName("A2 : Normalisation canonique IPv6 / IPv4:port empêche le contournement par variantes de notation")
    void testCanonicalIpNormalizationPreventsNotationSpoofing() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        RefreshRateLimiterService limiter = new RefreshRateLimiterService(mutableClock(now), 3, 60_000L, 100);

        limiter.checkAndRecordAttempt("::1");
        limiter.checkAndRecordAttempt("0:0:0:0:0:0:0:1");
        limiter.checkAndRecordAttempt("[::1]:54321");

        // 4e tentative via forme canonique complète doit être bloquée
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> limiter.checkAndRecordAttempt("0:0:0:0:0:0:0:1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
    }

    @Test
    @DisplayName("A2 : Purge des entrées expirées après écoulement de la fenêtre")
    void testExpiredEntriesEviction() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        RefreshRateLimiterService limiter = new RefreshRateLimiterService(mutableClock(now), 2, 60_000L, 100);

        limiter.checkAndRecordAttempt("41.82.10.1");
        limiter.checkAndRecordAttempt("41.82.10.1");
        assertThrows(ResponseStatusException.class, () -> limiter.checkAndRecordAttempt("41.82.10.1"));

        now.addAndGet(65_000L);
        assertDoesNotThrow(() -> limiter.checkAndRecordAttempt("41.82.10.1"));
    }
}
