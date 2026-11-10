package com.ecourtier.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Validateur de configuration de production.
 * 
 * En profil 'prod' ou 'production', vérifie que les secrets critiques ne reposent pas sur
 * des valeurs par défaut de développement ou des clés compromises.
 */
@Component
@Slf4j
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final Environment environment;

    @Value("${ecourtier.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    private static final List<String> KNOWN_INSECURE_SECRETS = List.of(
            "dev-secret-key-for-local-testing-only-replace-in-production-123456789",
            "secret",
            "password",
            "changeme",
            "12345678901234567890123456789012"
    );

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean isProduction = isProductionProfile();

        if (isProduction) {
            log.info("[VÉRIFICATION PRODUCTION] Profil de production détecté. Validation stricte des secrets...");

            // 1. Validation JWT_SECRET
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : La variable JWT_SECRET est obligatoire et ne peut pas être vide.");
            }
            if (KNOWN_INSECURE_SECRETS.contains(jwtSecret.trim())) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : JWT_SECRET utilise une clé de développement non sécurisée.");
            }
            if (jwtSecret.trim().length() < 32) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : JWT_SECRET doit comporter au moins 32 caractères (256 bits).");
            }

            // 2. Validation mot de passe base de données (pour PostgreSQL en production)
            String datasourceUrl = environment.getProperty("spring.datasource.url");
            boolean isPostgres = datasourceUrl != null && datasourceUrl.startsWith("jdbc:postgresql:");
            if (isPostgres && (dbPassword == null || dbPassword.isBlank())) {
                throw new IllegalStateException("ÉCHEC DÉMARRAGE PRODUCTION : Le mot de passe de la base de données (SPRING_DATASOURCE_PASSWORD) est obligatoire.");
            }

            log.info("[VÉRIFICATION PRODUCTION] Contrôles de sécurité validés avec succès.");
        } else {
            log.info("[MODE DÉVELOPPEMENT / TEST] Profils actifs : {}. Les contrôles stricts de production sont inactifs.",
                    Arrays.toString(environment.getActiveProfiles()));
        }
    }

    public boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}
