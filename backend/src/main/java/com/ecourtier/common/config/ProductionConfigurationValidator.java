package com.ecourtier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validateur de configuration de production.
 *
 * En profil 'prod' ou 'production', vérifie que les secrets critiques, les origines CORS
 * et les intégrations externes activées sont correctement configurés.
 */
@Component
@Slf4j
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final Environment environment;

    @Value("${ecourtier.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${ecourtier.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${ecourtier.ia.enabled:true}")
    private boolean iaEnabled = true;

    @Value("${ecourtier.ia.provider:groq}")
    private String iaProvider = "groq";

    @Value("${ecourtier.ia.groq.api-key:}")
    private String groqApiKey;

    @Value("${ecourtier.ia.api-key:}")
    private String iaApiKey;

    private static final List<String> KNOWN_INSECURE_SECRETS = List.of(
            "dev-secret-key-for-local-testing-only-replace-in-production-123456789",
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            "ecourtier_secure_pass_2026",
            "postgres",
            "ecourtier",
            "admin",
            "root",
            "secret",
            "password",
            "changeme",
            "12345678901234567890123456789012"
    );

    private static final Set<String> DISABLED_IA_PROVIDERS = Set.of("none", "disabled", "rules", "off", "false");

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProductionProfile()) {
            log.info("[MODE DÉVELOPPEMENT / TEST] Profils actifs : {}. Les contrôles stricts de production sont inactifs.",
                    Arrays.toString(environment.getActiveProfiles()));
            return;
        }

        log.info("[VÉRIFICATION PRODUCTION] Profil de production détecté. Validation stricte de la configuration...");

        validateJwtSecret();
        validateDatabasePassword();
        validateCorsAllowedOrigins();
        validateIaIntegrationConfiguration();

        log.info("[VÉRIFICATION PRODUCTION] Contrôles de sécurité validés avec succès.");
    }

    private void validateJwtSecret() {
        String secret = resolveText(jwtSecret, "ecourtier.jwt.secret");
        if (secret.isBlank()) {
            throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : La variable JWT_SECRET est obligatoire et ne peut pas être vide.");
        }
        if (KNOWN_INSECURE_SECRETS.contains(secret)) {
            throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : JWT_SECRET utilise une clé de développement non sécurisée.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : JWT_SECRET doit comporter au moins 32 caractères (256 bits).");
        }
    }

    private void validateDatabasePassword() {
        String datasourceUrl = environment.getProperty("spring.datasource.url");
        String normalizedUrl = datasourceUrl != null ? datasourceUrl.trim().toLowerCase(Locale.ROOT) : "";
        boolean isPostgres = normalizedUrl.startsWith("jdbc:postgresql:")
                || normalizedUrl.startsWith("postgres://")
                || normalizedUrl.startsWith("postgresql://");
        String password = resolveText(dbPassword, "spring.datasource.password");
        if (isPostgres) {
            if (password.isBlank()) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Le mot de passe de la base de données (SPRING_DATASOURCE_PASSWORD) est obligatoire.");
            }
            if (KNOWN_INSECURE_SECRETS.contains(password) || KNOWN_INSECURE_SECRETS.contains(password.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : SPRING_DATASOURCE_PASSWORD utilise une valeur de développement non sécurisée.");
            }
        }
    }

    private void validateCorsAllowedOrigins() {
        String rawOrigins = resolveText(corsAllowedOrigins, "ecourtier.cors.allowed-origins");
        if (rawOrigins.isBlank()) {
            throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : ECOURTIER_CORS_ALLOWED_ORIGINS est obligatoire et ne peut pas être vide.");
        }

        List<String> origins = Arrays.stream(rawOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.isEmpty()) {
            throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Aucune origine CORS valide définie dans ECOURTIER_CORS_ALLOWED_ORIGINS.");
        }

        for (String origin : origins) {
            if ("*".equals(origin) || origin.contains("*")) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Les jokers CORS (*) sont interdits en production.");
            }
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Origine CORS invalide (" + origin + ").", e);
            }
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            String normalizedHost = (host.startsWith("[") && host.endsWith("]"))
                    ? host.substring(1, host.length() - 1)
                    : host;
            if (!"https".equals(scheme) || normalizedHost.isBlank()) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Les origines CORS doivent utiliser HTTPS avec un hôte explicite (" + origin + ").");
            }
            if ("localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "0.0.0.0".equals(normalizedHost)
                    || "::1".equals(normalizedHost)
                    || "0:0:0:0:0:0:0:1".equals(normalizedHost)) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Les origines CORS locales (" + origin + ") sont interdites en production.");
            }
        }
    }

    private void validateIaIntegrationConfiguration() {
        Boolean enabledProp = environment.getProperty("ecourtier.ia.enabled", Boolean.class);
        boolean effectiveEnabled = enabledProp != null ? enabledProp : iaEnabled;

        String rawProvider = resolveText(iaProvider, "ecourtier.ia.provider");
        String normalizedProvider = rawProvider.isBlank() ? "groq" : rawProvider.toLowerCase(Locale.ROOT);

        if (!effectiveEnabled || DISABLED_IA_PROVIDERS.contains(normalizedProvider)) {
            log.info("[VÉRIFICATION PRODUCTION] Intégration LLM externe désactivée (ia.enabled={}, ia.provider={}) : clé API facultative.",
                    effectiveEnabled, normalizedProvider);
            return;
        }

        String effectiveKey = resolveText(groqApiKey, "ecourtier.ia.groq.api-key");
        if (effectiveKey.isBlank()) {
            effectiveKey = resolveText(iaApiKey, "ecourtier.ia.api-key");
        }
        if (effectiveKey.isBlank() || KNOWN_INSECURE_SECRETS.contains(effectiveKey)) {
            throw new IllegalStateException(
                    "ÉCHEC DÉMARRAGE PRODUCTION : L'intégration IA externe (provider=" + normalizedProvider
                            + ") est activée mais aucune clé API valide (GROQ_API_KEY / IA_API_KEY) n'est fournie."
            );
        }
    }

    private String resolveText(String fieldValue, String propertyName) {
        if (fieldValue != null && !fieldValue.isBlank()) {
            return fieldValue.trim();
        }
        String fromEnv = environment.getProperty(propertyName);
        return fromEnv != null ? fromEnv.trim() : "";
    }

    public boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}
