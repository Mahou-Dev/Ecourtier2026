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
 * et l'intégration IA (Google Gemini + secours déterministe local RuleBasedCriteriaExtractor)
 * sont correctement configurés sans jamais exposer les secrets.
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

    @Value("${ecourtier.ia.enabled:${IA_ENABLED:true}}")
    private boolean iaEnabled = true;

    @Value("${ecourtier.ia.provider:${AI_PROVIDER:${IA_PROVIDER:gemini}}}")
    private String iaProvider;

    @Value("${ecourtier.ia.gemini.api-key:${GEMINI_API_KEY:${GOOGLE_API_KEY:}}}")
    private String geminiApiKey;

    @Value("${ecourtier.ia.gemini.model:gemini-flash-lite-latest}")
    private String geminiModel;

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

    private static final Set<String> DISABLED_IA_PROVIDERS = Set.of("none", "disabled", "rules", "local", "off", "false");

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validatePayTechSandboxConfiguration();

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

    /**
     * Garde-fou Sandbox-only PayTech (MISSION RENDER-PAYTECH-01) :
     * Toute activation de PayTech hors environnement 'test' (ex: PAYTECH_ENV=prod) est strictement interdite.
     */
    private void validatePayTechSandboxConfiguration() {
        String provider = resolveText("", "ecourtier.payment.provider");
        if (provider.isBlank()) {
            provider = resolveText("", "PAYMENT_PROVIDER");
        }
        String normalizedProvider = provider.isBlank() ? "mock" : provider.toLowerCase(Locale.ROOT);
        if (!"paytech".equals(normalizedProvider)) {
            return;
        }

        String rawPropEnv = environment.getProperty("ecourtier.payment.paytech.env");
        String rawSysEnv = environment.getProperty("PAYTECH_ENV");
        String effectiveEnv;
        if (rawPropEnv != null) {
            effectiveEnv = rawPropEnv.trim().toLowerCase(Locale.ROOT);
        } else if (rawSysEnv != null) {
            effectiveEnv = rawSysEnv.trim().toLowerCase(Locale.ROOT);
        } else {
            effectiveEnv = "test";
        }
        if (!"test".equals(effectiveEnv)) {
            throw new IllegalStateException(
                    "ÉCHEC DÉMARRAGE SÉCURITÉ PAIEMENT : PAYTECH_ENV='" + sanitizeProviderLabel(effectiveEnv)
                            + "' est strictement interdit. Seul l'environnement Sandbox ('test') est autorisé."
            );
        }

        // AUDIT-PT-001 : En profil production, interdire toute activation de zero-debit-guaranteed=true vers paytech.sn
        Boolean zeroDebit = environment.getProperty("ecourtier.payment.paytech.zero-debit-guaranteed", Boolean.class);
        if (zeroDebit == null) {
            zeroDebit = environment.getProperty("PAYTECH_ZERO_DEBIT_GUARANTEED", Boolean.class);
        }
        String baseUrl = resolveText("", "ecourtier.payment.paytech.base-url");
        if (baseUrl.isBlank()) {
            baseUrl = resolveText("", "PAYTECH_BASE_URL");
        }
        if (isProductionProfile() && Boolean.TRUE.equals(zeroDebit)
                && (baseUrl.isBlank() || baseUrl.toLowerCase(Locale.ROOT).contains("paytech.sn"))) {
            throw new IllegalStateException(
                    "ÉCHEC DÉMARRAGE SÉCURITÉ ZÉRO DÉBIT : ecourtier.payment.paytech.zero-debit-guaranteed=true est interdit contre paytech.sn en profil production (risque de débit réel 100-150 FCFA)."
            );
        }

        boolean hasKey = !resolveText("", "ecourtier.payment.paytech.api-key").isBlank()
                || !resolveText("", "PAYTECH_API_KEY").isBlank();
        boolean hasSecret = !resolveText("", "ecourtier.payment.paytech.api-secret").isBlank()
                || !resolveText("", "PAYTECH_API_SECRET").isBlank();
        if (!hasKey || !hasSecret) {
            log.warn("[VÉRIFICATION PAIEMENT SANDBOX] PAYMENT_PROVIDER=paytech actif en mode env=test mais identifiants Sandbox incomplets (apiKeyPresent={}, apiSecretPresent={}). Aucun appel externe ne sera autorisé tant que les variables ne sont pas renseignées.",
                    hasKey, hasSecret);
        } else {
            log.info("[VÉRIFICATION PAIEMENT SANDBOX] Passerelle PayTech configurée en mode Sandbox strict (env=test, aucun débit de production autorisé).");
        }
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
        if (enabledProp == null) {
            enabledProp = environment.getProperty("IA_ENABLED", Boolean.class);
        }
        boolean effectiveEnabled = enabledProp != null ? enabledProp : iaEnabled;

        String rawProvider = resolveText(iaProvider, "ecourtier.ia.provider");
        if (rawProvider.isBlank()) {
            rawProvider = resolveText("", "AI_PROVIDER");
        }
        if (rawProvider.isBlank()) {
            rawProvider = resolveText("", "IA_PROVIDER");
        }
        String normalizedProvider = rawProvider.isBlank() ? "gemini" : rawProvider.toLowerCase(Locale.ROOT);

        if (!effectiveEnabled || DISABLED_IA_PROVIDERS.contains(normalizedProvider)) {
            log.info("[VÉRIFICATION PRODUCTION] Intégration LLM externe désactivée (ia.enabled={}, ia.provider={}) : moteur local déterministe RuleBasedCriteriaExtractor actif uniquement.",
                    effectiveEnabled, normalizedProvider);
            return;
        }

        if (!"gemini".equals(normalizedProvider)) {
            throw new IllegalStateException(
                    "ÉCHEC DÉMARRAGE PRODUCTION : Fournisseur IA incompatible '" + sanitizeProviderLabel(normalizedProvider)
                            + "'. Seul 'gemini' (ou 'local'/'disabled' avec IA_ENABLED=false) est autorisé."
            );
        }

        String keySource = "aucune";
        String effectiveGeminiKey = resolveText(geminiApiKey, "ecourtier.ia.gemini.api-key");
        if (!effectiveGeminiKey.isBlank()) {
            String rawEnvGemini = resolveText("", "GEMINI_API_KEY");
            String rawEnvGoogle = resolveText("", "GOOGLE_API_KEY");
            if (!rawEnvGemini.isBlank()) {
                keySource = "GEMINI_API_KEY";
            } else if (!rawEnvGoogle.isBlank()) {
                keySource = "GOOGLE_API_KEY";
            } else {
                keySource = "ecourtier.ia.gemini.api-key";
            }
        }
        if (effectiveGeminiKey.isBlank()) {
            effectiveGeminiKey = resolveText("", "GEMINI_API_KEY");
            if (!effectiveGeminiKey.isBlank()) keySource = "GEMINI_API_KEY";
        }
        if (effectiveGeminiKey.isBlank()) {
            effectiveGeminiKey = resolveText("", "GOOGLE_API_KEY");
            if (!effectiveGeminiKey.isBlank()) keySource = "GOOGLE_API_KEY";
        }
        if (effectiveGeminiKey.isBlank()) {
            String legacyIaKey = resolveText("", "IA_API_KEY");
            if (legacyIaKey.startsWith("AIza")) {
                effectiveGeminiKey = legacyIaKey;
                keySource = "IA_API_KEY(Google)";
            }
        }
        if (effectiveGeminiKey.isBlank()) {
            String[] candidateVars = {"GOOGLE_GEMINI_API_KEY", "GEMINI_KEY", "GOOGLE_AI_API_KEY", "AI_API_KEY", "API_KEY", "VITE_GEMINI_API_KEY"};
            for (String var : candidateVars) {
                String val = resolveText("", var);
                if (!val.isBlank() && !val.startsWith("gsk_")) {
                    effectiveGeminiKey = val;
                    keySource = var;
                    break;
                }
            }
        }

        if (effectiveGeminiKey.isBlank()) {
            log.warn("[VÉRIFICATION PRODUCTION] Variable GEMINI_API_KEY / GOOGLE_API_KEY absente (AI_PROVIDER=gemini, vérifié: GEMINI_API_KEY={}, GOOGLE_API_KEY={}, GOOGLE_GEMINI_API_KEY={}, IA_API_KEY={}) : démarrage autorisé en mode dégradé local sécurisé avec RuleBasedCriteriaExtractor uniquement.",
                    !resolveText("", "GEMINI_API_KEY").isBlank(),
                    !resolveText("", "GOOGLE_API_KEY").isBlank(),
                    !resolveText("", "GOOGLE_GEMINI_API_KEY").isBlank(),
                    !resolveText("", "IA_API_KEY").isBlank());
            return;
        }

        if (KNOWN_INSECURE_SECRETS.contains(effectiveGeminiKey)
                || KNOWN_INSECURE_SECRETS.contains(effectiveGeminiKey.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "ÉCHEC DÉMARRAGE PRODUCTION : La variable GEMINI_API_KEY utilise une valeur de développement ou de test non sécurisée."
            );
        }

        String effectiveModel = resolveText(geminiModel, "ecourtier.ia.gemini.model");
        if (effectiveModel.isBlank()) {
            effectiveModel = resolveText("", "GEMINI_MODEL");
        }
        log.info("[VÉRIFICATION PRODUCTION] Fournisseur IA Google Gemini configuré (source={}, modèle={}, secours=RuleBasedCriteriaExtractor, aucun fournisseur tiers requis).",
                keySource,
                effectiveModel.isBlank() ? "gemini-flash-lite-latest" : effectiveModel);
    }

    private String sanitizeProviderLabel(String providerLabel) {
        if (providerLabel == null || providerLabel.isBlank()) {
            return "inconnu";
        }
        String sanitized = providerLabel.replaceAll("[\\r\\n\\t]", "").trim();
        if (sanitized.length() > 32) {
            return sanitized.substring(0, 32);
        }
        return sanitized;
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
