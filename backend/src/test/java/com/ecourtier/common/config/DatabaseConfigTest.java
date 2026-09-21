package com.ecourtier.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseConfigTest {

    @Test
    @DisplayName("Vérifie la transformation propre de l'URL Render postgres://user:pass@host/db en URL JDBC PostgreSQL valide")
    void testFormatJdbcUrl() {
        String renderUrl = "postgres://ecourtier_user:EiDwNHfMckW15yRSPEsed51zNBuL2IBg@dpg-daokn0btqb8s73fo0cg0-a/ecourtier_db";
        String formatted = DatabaseConfig.formatJdbcUrl(renderUrl);
        
        assertEquals("jdbc:postgresql://dpg-daokn0btqb8s73fo0cg0-a:5432/ecourtier_db", formatted);
    }
}
