package com.ecourtier.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @Test
    @DisplayName("Vérifie la transformation propre de l'URL Render postgres://user:pass@host/db en URL JDBC PostgreSQL valide")
    void testFormatJdbcUrl() {
        String renderUrl = "postgres://ecourtier_user:redacted_test_placeholder@dpg-daokn0btqb8s73fo0cg0-a/ecourtier_db";
        String formatted = DatabaseConfig.formatJdbcUrl(renderUrl);

        assertEquals("jdbc:postgresql://dpg-daokn0btqb8s73fo0cg0-a:5432/ecourtier_db", formatted);
    }

    @Test
    @DisplayName("Ne force pas PostgreSQL lorsque org.h2.Driver est configuré")
    void testResolveJdbcUrlPreservesH2() {
        String explicitH2 = "jdbc:h2:mem:customdb;MODE=PostgreSQL";
        assertEquals(explicitH2, DatabaseConfig.resolveJdbcUrl(explicitH2, "org.h2.Driver"));

        String fallbackH2 = DatabaseConfig.resolveJdbcUrl("", "org.h2.Driver");
        assertTrue(fallbackH2.startsWith("jdbc:h2:mem:"));
    }

    @Test
    @DisplayName("Refuse une URL PostgreSQL vide lorsque le driver PostgreSQL est actif")
    void testResolveJdbcUrlRejectsBlankPostgresUrl() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.resolveJdbcUrl("", "org.postgresql.Driver"));
    }
}
