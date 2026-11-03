package com.ecourtier.common.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommonControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/health : renvoie UP et le statut du service")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("E-Courtier Backend")));
    }

    @Test
    @DisplayName("GET /api/v1/uploads/annonces/{id}/{filename} : 404 si fichier absent, 200 si présent")
    void testUploadController() throws Exception {
        // Fichier inexistant -> 404
        mockMvc.perform(get("/api/v1/uploads/annonces/test-id/nonexistent.jpg"))
                .andExpect(status().isNotFound());

        // Créer un fichier de test temporaire dans uploads/annonces/test-id/
        Path dir = Paths.get("uploads", "annonces", "test-id").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path testFile = dir.resolve("sample.png");
        Files.write(testFile, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        try {
            mockMvc.perform(get("/api/v1/uploads/annonces/test-id/sample.png"))
                    .andExpect(status().isOk());
        } finally {
            Files.deleteIfExists(testFile);
            Files.deleteIfExists(dir);
        }
    }
}
