package com.ecourtier.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ProductionConfigurationValidatorTest {

    private ProductionConfigurationValidator createValidProdValidator(Environment env) {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "aVeryStrongAndSecretProductionKeyWithEnoughEntropy2026!!");
        ReflectionTestUtils.setField(validator, "dbPassword", "ultra_secure_postgres_pass_2026");
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://ecourtier.sn,https://app.ecourtier.sn");
        ReflectionTestUtils.setField(validator, "iaEnabled", true);
        ReflectionTestUtils.setField(validator, "iaProvider", "groq");
        ReflectionTestUtils.setField(validator, "groqApiKey", "gsk_valid_production_test_placeholder_key_2026");
        return validator;
    }

    @Test
    @DisplayName("G-06 : En profil de production, un secret JWT vide bloque le démarrage (IllegalStateException)")
    void testEmptyJwtSecretInProdThrowsException() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    @DisplayName("G-06 : En profil de production, une clé JWT de développement connue bloque le démarrage")
    void testInsecureDevSecretInProdThrowsException() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "dev-secret-key-for-local-testing-only-replace-in-production-123456789");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(ex.getMessage().contains("clé de développement non sécurisée"));
    }

    @Test
    @DisplayName("A3 : En profil de production, un joker CORS (*) ou origine HTTP/localhost bloque le démarrage")
    void testCorsWildcardAndLocalhostBlockedInProd() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator wildcardValidator = createValidProdValidator(env);
        ReflectionTestUtils.setField(wildcardValidator, "corsAllowedOrigins", "*");
        IllegalStateException exWildcard = assertThrows(IllegalStateException.class,
                () -> wildcardValidator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(exWildcard.getMessage().contains("CORS"));

        ProductionConfigurationValidator localhostValidator = createValidProdValidator(env);
        ReflectionTestUtils.setField(localhostValidator, "corsAllowedOrigins", "https://localhost:5173");
        IllegalStateException exLocal = assertThrows(IllegalStateException.class,
                () -> localhostValidator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(exLocal.getMessage().contains("locales"));

        ProductionConfigurationValidator httpValidator = createValidProdValidator(env);
        ReflectionTestUtils.setField(httpValidator, "corsAllowedOrigins", "http://ecourtier.sn");
        IllegalStateException exHttp = assertThrows(IllegalStateException.class,
                () -> httpValidator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(exHttp.getMessage().contains("HTTPS"));
    }

    @Test
    @DisplayName("A3 : En profil de production, intégration IA désactivée sans clé API démarre sans erreur")
    void testProdWithIaDisabledWithoutKeySucceeds() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"production"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "iaEnabled", false);
        ReflectionTestUtils.setField(validator, "groqApiKey", "");
        ReflectionTestUtils.setField(validator, "iaApiKey", "");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));

        ProductionConfigurationValidator rulesValidator = createValidProdValidator(env);
        ReflectionTestUtils.setField(rulesValidator, "iaEnabled", true);
        ReflectionTestUtils.setField(rulesValidator, "iaProvider", "rules");
        ReflectionTestUtils.setField(rulesValidator, "groqApiKey", "");

        assertDoesNotThrow(() -> rulesValidator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("A3 : En profil de production, intégration IA activée sans clé API bloque le démarrage")
    void testProdWithIaEnabledWithoutKeyFails() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "iaEnabled", true);
        ReflectionTestUtils.setField(validator, "iaProvider", "groq");
        ReflectionTestUtils.setField(validator, "groqApiKey", "");
        ReflectionTestUtils.setField(validator, "iaApiKey", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(ex.getMessage().contains("GROQ_API_KEY"));
    }

    @Test
    @DisplayName("G-06 / A3 : En profil de production, configuration complète valide démarre sans erreur")
    void testValidProductionConfigSucceeds() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);

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
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "http://localhost:5173");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("CA3-007 : En profil de production, le secret JWT hex 64 caractères d'exemple bloque le démarrage")
    void testKnown64HexDevSecretInProdBlocked() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(ex.getMessage().contains("clé de développement non sécurisée"));
    }

    @Test
    @DisplayName("CA3-007 : En profil de production, une origine CORS IPv6 locale [::1] est bloquée")
    void testIpv6BracketLocalhostCorsBlockedInProd() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "corsAllowedOrigins", "https://[::1]:8443");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(ex.getMessage().contains("locale"));
    }

    @Test
    @DisplayName("CA3-007 : En profil de production, un mot de passe faible avec datasource postgres:// est bloqué")
    void testPostgresSchemeWeakPasswordBlockedInProd() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(env.getProperty("spring.datasource.url")).thenReturn("postgres://user:pass@localhost:5432/ecourtier");

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "dbPassword", "ecourtier");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(ex.getMessage().contains("SPRING_DATASOURCE_PASSWORD"));
    }
}
