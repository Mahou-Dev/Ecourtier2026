package com.ecourtier.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoginRateLimiterServiceTest {

    private LoginRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new LoginRateLimiterService();
    }

    @Test
    @DisplayName("Vérifie que 5 tentatives enregistrées fonctionnent et la 6ème lève une ResponseStatusException HTTP 429")
    void testRateLimiterBlocksAfterFiveFailures() {
        String ip = "192.168.1.50";
        String email = "test.rate@ecourtier.sn";

        // 5 tentatives autorisées
        for (int i = 0; i < 5; i++) {
            final int attempt = i + 1;
            assertDoesNotThrow(() -> rateLimiterService.checkAndRecordAttempt(ip, email),
                    "La tentative " + attempt + " doit passer sans exception");
        }

        // 6ème tentative bloquée avec HTTP 429
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> rateLimiterService.checkAndRecordAttempt(ip, email),
                "La 6ème tentative doit lever une ResponseStatusException (429)");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    @DisplayName("Réinitialisation du compteur lors d'une connexion réussie")
    void testResetOnSuccess() {
        String ip = "192.168.1.51";
        String email = "test.reset@ecourtier.sn";

        for (int i = 0; i < 4; i++) {
            rateLimiterService.checkAndRecordAttempt(ip, email);
        }

        rateLimiterService.resetAttempts(ip, email);

        // Après réinitialisation, 5 nouvelles tentatives sont permises
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> rateLimiterService.checkAndRecordAttempt(ip, email));
        }
    }

    @Test
    @DisplayName("ANO-06 : Purge automatique des entrées expirées sans supprimer les compteurs encore actifs")
    void testEvictExpiredEntriesPreservesActiveCounters() {
        java.util.concurrent.atomic.AtomicLong currentMillis = new java.util.concurrent.atomic.AtomicLong(1_700_000_000_000L);
        java.time.Clock mutableClock = new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public java.time.Instant instant() {
                return java.time.Instant.ofEpochMilli(currentMillis.get());
            }
        };

        LoginRateLimiterService clockService = new LoginRateLimiterService(mutableClock);

        // t = 0 : 5 tentatives pour user1 (bloqué)
        for (int i = 0; i < 5; i++) {
            clockService.checkAndRecordAttempt("192.168.1.10", "user1@ecourtier.sn");
        }
        assertThrows(ResponseStatusException.class,
                () -> clockService.checkAndRecordAttempt("192.168.1.10", "user1@ecourtier.sn"));

        // t = +10 minutes : 5 tentatives pour user2 (encore actif)
        currentMillis.addAndGet(10 * 60 * 1000L);
        for (int i = 0; i < 5; i++) {
            clockService.checkAndRecordAttempt("192.168.1.20", "user2@ecourtier.sn");
        }
        assertEquals(2, clockService.getTrackedEntriesCount());

        // t = +16 minutes depuis t=0 (user1 est expiré > 15 min, user2 est à 6 min donc encore actif)
        currentMillis.addAndGet(6 * 60 * 1000L);
        clockService.evictExpiredEntries();

        // L'entrée expirée de user1 a été purgée, seule celle de user2 subsiste
        assertEquals(1, clockService.getTrackedEntriesCount());

        // user2 est toujours bloqué (fenêtre active)
        assertThrows(ResponseStatusException.class,
                () -> clockService.checkAndRecordAttempt("192.168.1.20", "user2@ecourtier.sn"));

        // user1 peut de nouveau tenter de se connecter
        assertDoesNotThrow(() -> clockService.checkAndRecordAttempt("192.168.1.10", "user1@ecourtier.sn"));
    }
}
