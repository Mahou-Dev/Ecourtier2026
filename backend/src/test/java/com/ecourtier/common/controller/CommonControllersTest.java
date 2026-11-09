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

    @Autowired
    private UploadController uploadController;

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

    @Test
    @DisplayName("ANO-12 : Rejet des chemins malveillants (path traversal) et impossibilité de lire les documents privés via UploadController")
    void testUploadControllerPathTraversalRejected() throws Exception {
        // Créer un document justificatif privé dans uploads/documents/test-doc-id/secret.pdf
        Path privateDocDir = Paths.get("uploads", "documents", "test-doc-id").toAbsolutePath().normalize();
        Files.createDirectories(privateDocDir);
        Path privateDoc = privateDocDir.resolve("secret.pdf");
        Files.write(privateDoc, "%PDF-1.4 secret".getBytes());

        try {
            // Tentatives de path traversal via le contrôleur (appel direct + MockMvc) -> 400 Bad Request
            org.junit.jupiter.api.Assertions.assertEquals(400,
                    uploadController.getPhoto("..", "secret.pdf").getStatusCode().value());
            org.junit.jupiter.api.Assertions.assertEquals(400,
                    uploadController.getPhoto("../documents/test-doc-id", "secret.pdf").getStatusCode().value());
            org.junit.jupiter.api.Assertions.assertEquals(400,
                    uploadController.getPhoto("test-id", "../../documents/test-doc-id/secret.pdf").getStatusCode().value());
            org.junit.jupiter.api.Assertions.assertEquals(400,
                    uploadController.getPhoto("test-id", "..\\..\\documents\\test-doc-id\\secret.pdf").getStatusCode().value());
            org.junit.jupiter.api.Assertions.assertEquals(400,
                    uploadController.getPhoto("test-id", "sub/sample.png").getStatusCode().value());

            mockMvc.perform(get("/api/v1/uploads/annonces/{id}/{filename}", "..", "secret.pdf"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.junit.jupiter.api.Assertions.assertTrue(status == 400 || status == 404);
                    });
        } finally {
            Files.deleteIfExists(privateDoc);
            Files.deleteIfExists(privateDocDir);
        }
    }

    @Test
    @DisplayName("ANO-15 : GET /openapi.json est accessible publiquement (200 OK) sans réduire la sécurité des routes privées")
    void testPublicOpenApiJsonAccessible() throws Exception {
        mockMvc.perform(get("/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());

        // Une ressource privée reste protégée (401)
        mockMvc.perform(get("/api/v1/utilisateurs/moi/favoris"))
                .andExpect(status().isUnauthorized());
    }
}
