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
}
