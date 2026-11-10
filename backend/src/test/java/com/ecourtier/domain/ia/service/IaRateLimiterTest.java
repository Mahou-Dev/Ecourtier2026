package com.ecourtier.domain.ia.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class IaRateLimiterTest {

    private AtomicLong currentMillis;
    private Clock mutableClock;
    private IaRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        currentMillis = new AtomicLong(1_700_000_000_000L);
        mutableClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(currentMillis.get());
            }
        };

        rateLimiterService = new IaRateLimiterService(mutableClock);
    }

    @Test
    @DisplayName("G-02 : Les utilisateurs anonymes sont limités à 10 requêtes par minute (la 11ème lève HTTP 429)")
    void testAnonymousQuotaExceededThrows429() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        // 10 requêtes autorisées
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(request, null));
        }

        // 11ème requête bloquée
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> rateLimiterService.checkRateLimit(request, null));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());

        // Après 61 secondes (fenêtre expirée), une nouvelle requête est autorisée
        currentMillis.addAndGet(61_000L);
        assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(request, null));
    }

    @Test
    @DisplayName("G-02 : Les utilisateurs authentifiés bénéficient d'un quota supérieur de 30 requêtes par minute (la 31ème lève HTTP 429)")
    void testAuthenticatedQuotaExceededThrows429() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");

        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("locataire.dakar@test.sn");

        // 30 requêtes autorisées
        for (int i = 0; i < 30; i++) {
            assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(request, auth));
        }

        // 31ème requête bloquée
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> rateLimiterService.checkRateLimit(request, auth));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    @DisplayName("G-02 : Deux utilisateurs distincts ont des compteurs séparés étanches")
    void testDistinctClientsHaveIndependentQuotas() {
        HttpServletRequest request1 = Mockito.mock(HttpServletRequest.class);
        when(request1.getRemoteAddr()).thenReturn("10.0.0.1");

        HttpServletRequest request2 = Mockito.mock(HttpServletRequest.class);
        when(request2.getRemoteAddr()).thenReturn("10.0.0.2");

        // Saturer request1 (10 requêtes)
        for (int i = 0; i < 10; i++) {
            rateLimiterService.checkRateLimit(request1, null);
        }
        assertThrows(ResponseStatusException.class, () -> rateLimiterService.checkRateLimit(request1, null));

        // request2 peut toujours consommer ses 10 requêtes
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(request2, null));
        }
        assertThrows(ResponseStatusException.class, () -> rateLimiterService.checkRateLimit(request2, null));
    }
}
