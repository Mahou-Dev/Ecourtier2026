package com.ecourtier.common.security;

import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private UtilisateurEntity testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Set secret key and expiration values via Reflection
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", 3600000L); // 1 hour
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpirationMs", 604800000L); // 7 days

        testUser = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .nom("Diop")
                .prenom("Moussa")
                .email("moussa.diop@ecourtier.sn")
                .motDePasse("hashed_pass")
                .telephone("+221 77 123 45 67")
                .role(Role.LOCATAIRE)
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("Génération et validation d'un Token d'accès JWT")
    void testGenerateAccessTokenAndValidate() {
        String token = jwtService.generateAccessToken(testUser);

        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token, testUser.getEmail()));
        assertEquals(testUser.getEmail(), jwtService.extractEmail(token));
        assertEquals(Role.LOCATAIRE.name(), jwtService.extractRole(token));
        assertEquals(testUser.getId(), jwtService.extractUserId(token));
        assertFalse(jwtService.isRefreshToken(token));
    }

    @Test
    @DisplayName("Génération d'un Refresh Token JWT et vérification du type")
    void testGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertNotNull(refreshToken);
        assertTrue(jwtService.isRefreshToken(refreshToken));
        assertEquals(testUser.getEmail(), jwtService.extractEmail(refreshToken));
    }

    @Test
    @DisplayName("Invalidation d'un Token expiré ou altéré")
    void testInvalidToken() {
        String invalidToken = "invalid.jwt.token";
        assertFalse(jwtService.isTokenValid(invalidToken));

        // Test with short expiration
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", -1000L);
        String expiredToken = jwtService.generateAccessToken(testUser);
        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    @DisplayName("Refus de démarrer si JWT_SECRET est absent, vide ou trop court")
    void testRefusDemarrageSiJwtSecretAbsentOuFaible() {
        JwtService missingSecretService = new JwtService();
        ReflectionTestUtils.setField(missingSecretService, "secretKey", "");
        IllegalStateException emptyEx = assertThrows(IllegalStateException.class, missingSecretService::validateSecretKey);
        assertTrue(emptyEx.getMessage().contains("JWT_SECRET"));

        ReflectionTestUtils.setField(missingSecretService, "secretKey", null);
        assertThrows(IllegalStateException.class, missingSecretService::validateSecretKey);

        ReflectionTestUtils.setField(missingSecretService, "secretKey", "short_secret");
        assertThrows(IllegalStateException.class, missingSecretService::validateSecretKey);

        // Vérifie l'échec d'initialisation du contexte Spring si ecourtier.jwt.secret est vide (profil prod)
        org.springframework.boot.test.context.runner.ApplicationContextRunner contextRunner =
                new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                        .withUserConfiguration(JwtService.class)
                        .withPropertyValues("ecourtier.jwt.secret=");

        contextRunner.run(context -> {
            assertNotNull(context.getStartupFailure(), "Le contexte Spring doit refuser de démarrer sans JWT_SECRET");
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertInstanceOf(IllegalStateException.class, root);
            assertTrue(root.getMessage().contains("JWT_SECRET"));
        });
    }
}
