package com.ecourtier.common.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadSecurityTest {

    private UploadController uploadController;
    private Path tempUploadsDir;
    private Path externalSecretFile;

    @BeforeEach
    void setUp() throws IOException {
        uploadController = new UploadController();

        // Créer un fichier externe sensible simulant /etc/passwd ou des clés privées
        externalSecretFile = Files.createTempFile("external-secret-", ".txt");
        Files.writeString(externalSecretFile, "SECRET_TOKEN_DO_NOT_LEAK");

        tempUploadsDir = Paths.get("uploads", "annonces", "test-annonce-uuid");
        Files.createDirectories(tempUploadsDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (externalSecretFile != null) {
            Files.deleteIfExists(externalSecretFile);
        }
        if (tempUploadsDir != null && Files.exists(tempUploadsDir)) {
            // Nettoyer les fichiers de test créés
            try (var stream = Files.walk(tempUploadsDir)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("G-03 : Un fichier légitime présent dans uploads/annonces est servi normalement (200 OK)")
    void testNormalPhotoDownloadSucceeds() throws IOException {
        Path legitimatePhoto = tempUploadsDir.resolve("photo-123.jpg");
        Files.write(legitimatePhoto, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        ResponseEntity<Resource> response = uploadController.getPhoto("test-annonce-uuid", "photo-123.jpg");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().exists());
    }

    @Test
    @DisplayName("G-03 : Tentative de Path Traversal avec '../' rejetée avec HTTP 400 Bad Request")
    void testDotDotTraversalRejected() {
        ResponseEntity<Resource> response = uploadController.getPhoto("test-annonce-uuid", "../secret.txt");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ResponseEntity<Resource> responseId = uploadController.getPhoto("../autre-dossier", "photo.jpg");
        assertEquals(HttpStatus.BAD_REQUEST, responseId.getStatusCode());
    }

    @Test
    @DisplayName("G-03 : Tentative avec séparateurs interdits (/, \\, :) rejetée avec HTTP 400")
    void testForbiddenSeparatorsRejected() {
        ResponseEntity<Resource> responseSlash = uploadController.getPhoto("test-annonce-uuid", "sub/photo.jpg");
        assertEquals(HttpStatus.BAD_REQUEST, responseSlash.getStatusCode());

        ResponseEntity<Resource> responseBackslash = uploadController.getPhoto("test-annonce-uuid", "sub\\photo.jpg");
        assertEquals(HttpStatus.BAD_REQUEST, responseBackslash.getStatusCode());

        ResponseEntity<Resource> responseColon = uploadController.getPhoto("test-annonce-uuid", "C:photo.jpg");
        assertEquals(HttpStatus.BAD_REQUEST, responseColon.getStatusCode());
    }

    @Test
    @DisplayName("G-03 : Lien symbolique pointant vers un fichier extérieur strictement refusé (400 Bad Request)")
    void testSymlinkToExternalFileRefused() throws IOException {
        Path symlinkFile = tempUploadsDir.resolve("symlink-to-secret.jpg");
        try {
            Files.createSymbolicLink(symlinkFile, externalSecretFile);
        } catch (UnsupportedOperationException | IOException e) {
            // Environnement restreint sans création de symlink : tester la détection
            return;
        }

        ResponseEntity<Resource> response = uploadController.getPhoto("test-annonce-uuid", "symlink-to-secret.jpg");

        // Le contrôleur DOIT refuser de servir le lien symbolique
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        // Vérifier que le fichier externe secret n'a pas été altéré
        assertTrue(Files.exists(externalSecretFile));
    }

    @Test
    @DisplayName("G-03 : Fichier inexistant retourne HTTP 404 Not Found")
    void testNonExistentFileReturns404() {
        ResponseEntity<Resource> response = uploadController.getPhoto("test-annonce-uuid", "inexistant-404.jpg");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("G-03 : FileStorageService refuse le chargement et la suppression via lien symbolique")
    void testFileStorageServiceRefusesSymlinks() throws IOException {
        com.ecourtier.common.service.FileStorageService storageService = new com.ecourtier.common.service.FileStorageService();
        Path symlinkFile = tempUploadsDir.resolve("symlink-storage.jpg");
        try {
            Files.createSymbolicLink(symlinkFile, externalSecretFile);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        String relativeUrl = "/api/v1/uploads/annonces/test-annonce-uuid/symlink-storage.jpg";

        // 1. Chargement refusé
        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> storageService.loadFileAsResource(relativeUrl)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // 2. Suppression refusée (le fichier cible externe ne doit pas être supprimé)
        boolean deleted = storageService.deleteFile(relativeUrl);
        assertFalse(deleted);
        assertTrue(Files.exists(externalSecretFile));
    }
}
