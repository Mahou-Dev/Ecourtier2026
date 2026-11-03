package com.ecourtier.common.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "ADMIN_BOOTSTRAP_PASSWORD=",
    "JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
    "spring.datasource.url=jdbc:postgresql://${DB_HOST:ecourtier-db}:${DB_PORT:5432}/ecourtier_v11_fresh",
    "spring.datasource.username=ecourtier_user",
    "spring.datasource.password=ecourtier_secure_pass_2026",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@DirtiesContext
public class FlywayFreshMigrationIntegrationTest {

    @Autowired(required = false)
    private Flyway flyway;

    @Test
    @DisplayName("Vérifie l'exécution sans erreur de toutes les migrations Flyway (V1 à V12) sur une base PostgreSQL vierge")
    void testFlywayMigrationsOnFreshDatabase() {
        assertNotNull(flyway, "Flyway bean doit être disponible");
        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "Une version courante de migration doit être enregistrée");
        assertEquals("12", current.getVersion().getVersion(), "La dernière migration appliquée doit être la V12");
        assertTrue(current.getState().isApplied(), "La migration V12 doit être appliquée avec succès");
    }
}
