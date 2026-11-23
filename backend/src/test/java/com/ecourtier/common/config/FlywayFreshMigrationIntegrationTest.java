package com.ecourtier.common.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "ADMIN_BOOTSTRAP_PASSWORD=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
@Testcontainers
@DirtiesContext
@EnabledIfSystemProperty(named = "ecourtier.test.pg16", matches = "true")
public class FlywayFreshMigrationIntegrationTest {

    static {
        if (System.getProperty("api.version") == null) {
            System.setProperty("api.version", "1.44");
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("ecourtier_flyway_fresh")
            .withUsername("ecourtier_test")
            .withPassword("ecourtier_test_pwd");

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired(required = false)
    private Flyway flyway;

    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("CA3-011: Vérifie l'exécution sans erreur de toutes les migrations Flyway (V1 à V22) sur PostgreSQL 16 vierge isolé")
    void testFlywayMigrationsOnFreshDatabase() {
        assertNotNull(flyway, "Flyway bean doit être disponible");
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "Une version courante de migration doit être enregistrée");
        assertEquals("22", current.getVersion().getVersion(), "La dernière migration appliquée doit être la V22");
        assertTrue(current.getState().isApplied(), "La migration V22 doit être appliquée avec succès");

        MigrationInfo[] applied = flyway.info().applied();
        assertEquals(22, applied.length, "Toutes les migrations V1 à V22 doivent être appliquées");
        for (int v = 1; v <= 22; v++) {
            MigrationInfo migration = applied[v - 1];
            assertEquals(String.valueOf(v), migration.getVersion().getVersion(),
                    "La migration V" + v + " doit être présente dans l'ordre");
            assertTrue(migration.getState().isApplied(),
                    "La migration V" + v + " doit être appliquée avec succès");
        }

        assertNotNull(jdbcTemplate);
        Integer constraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uq_messages_ia_conversation_ordre'",
                Integer.class
        );
        assertEquals(1, constraintCount,
                "La contrainte unique uq_messages_ia_conversation_ordre (V22) doit exister sur PostgreSQL 16");
    }
}
