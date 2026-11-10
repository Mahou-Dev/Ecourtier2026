package com.ecourtier.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ProductionConfigurationValidatorTest {

    @Test
    @DisplayName("G-06 : En profil de production, un secret JWT vide bloque le démarrage (IllegalStateException)")
    void testEmptyJwtSecretInProdThrowsException() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        ReflectionTestUtils.setField(validator, "dbPassword", "real_secure_db_pass_123");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    @DisplayName("G-06 : En profil de production, une clé JWT de développement connue bloque le démarrage")
    void testInsecureDevSecretInProdThrowsException() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "dev-secret-key-for-local-testing-only-replace-in-production-123456789");
        ReflectionTestUtils.setField(validator, "dbPassword", "real_secure_db_pass_123");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(ex.getMessage().contains("clé de développement non sécurisée"));
    }

    @Test
    @DisplayName("G-06 : En profil de production, une clé valide >= 32 caractères et mot de passe DB valide démarre sans erreur")
    void testValidProductionConfigSucceeds() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "aVeryStrongAndSecretProductionKeyWithEnoughEntropy2026!!");
        ReflectionTestUtils.setField(validator, "dbPassword", "ultra_secure_postgres_pass_2026");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("G-06 : En profil dev/test, les contrôles stricts de production ne s'exécutent pas")
    void testDevProfileBypassesStrictProductionChecks() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "dev-secret-key-for-local-testing-only-replace-in-production-123456789");
        ReflectionTestUtils.setField(validator, "dbPassword", "");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }
}
