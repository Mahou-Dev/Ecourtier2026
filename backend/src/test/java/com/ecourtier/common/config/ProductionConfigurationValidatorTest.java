package com.ecourtier.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        ReflectionTestUtils.setField(validator, "iaProvider", "gemini");
        ReflectionTestUtils.setField(validator, "geminiApiKey", "AIzaSyDummyValidTestPlaceholderKeyForProd2026");
        ReflectionTestUtils.setField(validator, "geminiModel", "gemini-flash-lite-latest");
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
    @DisplayName("RENDER-GEMINI-01 : En profil de production, intégration IA désactivée ou en mode local démarre sans clé Gemini")
    void testProdWithIaDisabledWithoutKeySucceeds() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"production"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "iaEnabled", false);
        ReflectionTestUtils.setField(validator, "geminiApiKey", "");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));

        ProductionConfigurationValidator rulesValidator = createValidProdValidator(env);
        ReflectionTestUtils.setField(rulesValidator, "iaEnabled", true);
        ReflectionTestUtils.setField(rulesValidator, "iaProvider", "local");
        ReflectionTestUtils.setField(rulesValidator, "geminiApiKey", "");

        assertDoesNotThrow(() -> rulesValidator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : En profil prod Render, GEMINI_API_KEY absente n'empêche pas le démarrage (secours local)")
    void testProdWithGeminiWithoutKeyStartsInFallbackMode() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "iaEnabled", true);
        ReflectionTestUtils.setField(validator, "iaProvider", "gemini");
        ReflectionTestUtils.setField(validator, "geminiApiKey", "");

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("RENDER-GEMINI-02 Cas 3 : Un fournisseur incompatible (groq/openai/claude/autre) en prod échoue explicitement sans bascule")
    void testProdRejectsIncompatibleProvidersExplicitly() {
        for (String legacyProvider : new String[]{"groq", "openai", "claude", "unknown"}) {
            Environment env = Mockito.mock(Environment.class);
            when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

            ProductionConfigurationValidator validator = createValidProdValidator(env);
            ReflectionTestUtils.setField(validator, "iaProvider", legacyProvider);
            ReflectionTestUtils.setField(validator, "geminiApiKey", "");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> validator.run(new DefaultApplicationArguments(new String[0])),
                    "Le fournisseur incompatible " + legacyProvider + " doit être rejeté explicitement en production");
            assertTrue(ex.getMessage().contains("Fournisseur IA incompatible"));
            assertFalse(ex.getMessage().contains("GROQ_API_KEY"));
            assertFalse(ex.getMessage().contains("IA_API_KEY"));
        }
    }

    @Test
    @DisplayName("RENDER-GEMINI-02 Cas 1 : Configuration Render exacte via Environment (sans GROQ_API_KEY ni IA_API_KEY) acceptée en prod")
    void testProdRenderEnvironmentVariablesAcceptedWithoutGroqOrIaApiKey() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ecourtier.jwt.secret", "aVeryStrongAndSecretProductionKeyWithEnoughEntropy2026!!");
        env.setProperty("spring.datasource.password", "ultra_secure_postgres_pass_2026");
        env.setProperty("ecourtier.cors.allowed-origins", "https://ecourtier-frontend.onrender.com");
        env.setProperty("IA_ENABLED", "true");
        env.setProperty("IA_PROVIDER", "gemini");
        env.setProperty("AI_PROVIDER", "gemini");
        env.setProperty("GEMINI_API_KEY", "dummy-valid-render-gemini-test-key-2026");
        env.setProperty("GEMINI_MODEL", "gemini-flash-lite-latest");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("RENDER-GEMINI-01 : Une valeur de développement connue pour GEMINI_API_KEY en prod est bloquée sans fuite")
    void testProdWithInsecurePlaceholderGeminiKeyThrowsWithoutLeakingKey() {
        Environment env = Mockito.mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        String insecurePlaceholder = "changeme";
        ProductionConfigurationValidator validator = createValidProdValidator(env);
        ReflectionTestUtils.setField(validator, "iaEnabled", true);
        ReflectionTestUtils.setField(validator, "iaProvider", "gemini");
        ReflectionTestUtils.setField(validator, "geminiApiKey", insecurePlaceholder);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(ex.getMessage().contains("GEMINI_API_KEY"));
        assertFalse(ex.getMessage().contains(insecurePlaceholder), "Le secret ne doit jamais être exposé");
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

    @Test
    @DisplayName("RENDER-GEMINI-02 : Injection @Value réelle depuis application.yml + application-prod.yml avec les variables Render exactes")
    void testSpringProdYamlBindingWithRenderGeminiVariablesSucceeds() throws Exception {
        assertDoesNotThrow(() -> runValidatorWithProdYaml(true));
    }

    @Test
    @DisplayName("RENDER-GEMINI-02 : Injection @Value réelle depuis application-prod.yml sans GEMINI_API_KEY ni GROQ_API_KEY démarre en fallback local")
    void testSpringProdYamlBindingWithRenderMissingGeminiKeyStartsInFallbackMode() throws Exception {
        assertDoesNotThrow(() -> runValidatorWithProdYaml(false));
    }

    @Test
    @DisplayName("RENDER-PAYTECH-01 : PAYTECH_ENV=prod est strictement bloqué au démarrage (même hors profil prod)")
    void testPayTechProdEnvBlockedAtStartup() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("ecourtier.payment.provider", "paytech");
        env.setProperty("ecourtier.payment.paytech.env", "prod");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(ex.getMessage().contains("PAYTECH_ENV"));
        assertTrue(ex.getMessage().contains("Sandbox"));
    }

    @Test
    @DisplayName("RENDER-PAYTECH-01 : PAYTECH_ENV=test démarre proprement en Sandbox sans bloquer si mock ou clés test")
    void testPayTechSandboxEnvAllowedAtStartup() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("ecourtier.payment.provider", "paytech");
        env.setProperty("ecourtier.payment.paytech.env", "test");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    @DisplayName("AUDIT-PT-011 : PAYTECH_ENV vide explicite est rejeté au démarrage lorsque PAYMENT_PROVIDER=paytech")
    void testPayTechBlankEnvBlockedAtStartup() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("ecourtier.payment.provider", "paytech");
        env.setProperty("ecourtier.payment.paytech.env", "");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(ex.getMessage().contains("PAYTECH_ENV"));
    }

    @Test
    @DisplayName("AUDIT-PT-001 : En profil production, zero-debit-guaranteed=true vers paytech.sn est bloqué au démarrage")
    void testPayTechZeroDebitTrueAgainstPaytechSnBlockedInProd() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ecourtier.jwt.secret", "aVeryStrongAndSecretProductionKeyWithEnoughEntropy2026!!");
        env.setProperty("spring.datasource.password", "ultra_secure_postgres_pass_2026");
        env.setProperty("ecourtier.cors.allowed-origins", "https://ecourtier-frontend.onrender.com");
        env.setProperty("ecourtier.payment.provider", "paytech");
        env.setProperty("ecourtier.payment.paytech.env", "test");
        env.setProperty("ecourtier.payment.paytech.base-url", "https://paytech.sn/api");
        env.setProperty("ecourtier.payment.paytech.zero-debit-guaranteed", "true");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(env);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator.run(new DefaultApplicationArguments(new String[0])));
        assertTrue(ex.getMessage().contains("zero-debit-guaranteed"));
    }

    private void runValidatorWithProdYaml(boolean includeGeminiKey) throws Exception {
        org.springframework.boot.env.YamlPropertySourceLoader loader = new org.springframework.boot.env.YamlPropertySourceLoader();
        java.util.List<org.springframework.core.env.PropertySource<?>> baseSources =
                loader.load("application", new org.springframework.core.io.ClassPathResource("application.yml"));
        java.util.List<org.springframework.core.env.PropertySource<?>> prodSources =
                loader.load("application-prod", new org.springframework.core.io.ClassPathResource("application-prod.yml"));

        try (org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
                     new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().setActiveProfiles("prod");
            java.util.Map<String, Object> renderEnv = new java.util.HashMap<>();
            renderEnv.put("SPRING_DATASOURCE_URL", "postgres://user:pass@dpg-example:5432/ecourtier_db");
            renderEnv.put("SPRING_DATASOURCE_PASSWORD", "ultra_secure_postgres_pass_2026");
            renderEnv.put("JWT_SECRET", "aVeryStrongAndSecretProductionKeyWithEnoughEntropy2026!!");
            renderEnv.put("ECOURTIER_CORS_ALLOWED_ORIGINS", "https://ecourtier-frontend.onrender.com");
            renderEnv.put("IA_ENABLED", "true");
            renderEnv.put("IA_PROVIDER", "gemini");
            renderEnv.put("AI_PROVIDER", "gemini");
            renderEnv.put("GEMINI_MODEL", "gemini-flash-lite-latest");
            renderEnv.put("GEMINI_TIMEOUT_MS", "4000");
            if (includeGeminiKey) {
                renderEnv.put("GEMINI_API_KEY", "dummy-valid-render-gemini-test-key-2026");
            }
            ctx.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("renderMockEnv", renderEnv)
            );
            for (org.springframework.core.env.PropertySource<?> ps : prodSources) {
                ctx.getEnvironment().getPropertySources().addAfter("renderMockEnv", ps);
            }
            for (org.springframework.core.env.PropertySource<?> ps : baseSources) {
                ctx.getEnvironment().getPropertySources().addLast(ps);
            }
            ctx.registerBean(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class);
            ctx.registerBean(ProductionConfigurationValidator.class);
            ctx.refresh();

            ProductionConfigurationValidator validator = ctx.getBean(ProductionConfigurationValidator.class);
            validator.run(new DefaultApplicationArguments(new String[0]));
        }
    }
}
