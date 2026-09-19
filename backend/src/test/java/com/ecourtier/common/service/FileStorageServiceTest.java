package com.ecourtier.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
    }

    @Test
    @DisplayName("Refus d'un fichier null ou vide (HTTP 400)")
    void testValidateFileEmptyOrNull() {
        ResponseStatusException exNull = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(UUID.randomUUID(), null));
        assertEquals(400, exNull.getStatusCode().value());

        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        ResponseStatusException exEmpty = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(UUID.randomUUID(), emptyFile));
        assertEquals(400, exEmpty.getStatusCode().value());
    }

    @Test
    @DisplayName("Refus d'un fichier dépassant la taille maximale 5MB (HTTP 400)")
    void testValidateFileExceedsMaxSize() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.jpg", "image/jpeg", largeContent);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(UUID.randomUUID(), largeFile));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Refus d'un format d'extension non autorisé (pdf, exe, sans extension)")
    void testValidateFileExtensionNotAllowed() {
        byte[] content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile pdfFile = new MockMultipartFile("file", "document.pdf", "application/pdf", content);

        ResponseStatusException exPdf = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(UUID.randomUUID(), pdfFile));
        assertEquals(400, exPdf.getStatusCode().value());

        MockMultipartFile noExtFile = new MockMultipartFile("file", "noextension", "image/jpeg", content);
        ResponseStatusException exNoExt = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(UUID.randomUUID(), noExtFile));
        assertEquals(400, exNoExt.getStatusCode().value());
    }

    @Test
    @DisplayName("Refus si le fichier fait moins de 4 octets (content trop court pour magic bytes)")
    void testValidateFileShortContent() {
        byte[] shortContent = new byte[]{(byte) 0xFF, (byte) 0xD8};
        MockMultipartFile shortFile = new MockMultipartFile("file", "short.jpg", "image/jpeg", shortContent);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(UUID.randomUUID(), shortFile));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Validation réussie des Magic Bytes JPEG, PNG et WEBP")
    void testMagicBytesValidFormats() {
        UUID annonceId = UUID.randomUUID();

        // 1. JPEG (FF D8 FF ...)
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11, 0x22};
        MockMultipartFile jpegFile = new MockMultipartFile("file", "photo.jpeg", "image/jpeg", jpegHeader);
        String jpegPath = assertDoesNotThrow(() -> fileStorageService.storePhotoAnnonce(annonceId, jpegFile));
        assertTrue(jpegPath.contains("/api/v1/uploads/annonces/" + annonceId));

        // 2. PNG (89 50 4E 47 ...)
        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile pngFile = new MockMultipartFile("file", "photo.png", "image/png", pngHeader);
        String pngPath = assertDoesNotThrow(() -> fileStorageService.storeDocumentVerification(annonceId, pngFile));
        assertTrue(pngPath.contains("/api/v1/uploads/documents/" + annonceId));

        // 3. WEBP (RIFF....WEBP)
        byte[] webpHeader = new byte[]{
                'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'
        };
        MockMultipartFile webpFile = new MockMultipartFile("file", "photo.webp", "image/webp", webpHeader);
        String webpPath = assertDoesNotThrow(() -> fileStorageService.storePhotoAnnonce(annonceId, webpFile));
        assertTrue(webpPath.contains("/api/v1/uploads/annonces/" + annonceId));
    }

    @Test
    @DisplayName("Contrôle de sécurité Path Traversal dans deleteFile & loadFileAsResource")
    void testPathTraversalProtection() throws IOException {
        // 1. deleteFile avec null ou vide -> false
        assertFalse(fileStorageService.deleteFile(null));
        assertFalse(fileStorageService.deleteFile(""));

        // 2. deleteFile avec tentative de path traversal (../)
        assertFalse(fileStorageService.deleteFile("/api/v1/uploads/../../etc/passwd"));

        // 3. loadFileAsResource avec tentative de path traversal -> 400 Bad Request
        ResponseStatusException exTraversal = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.loadFileAsResource("/api/v1/uploads/../../etc/passwd"));
        assertEquals(400, exTraversal.getStatusCode().value());

        // 4. loadFileAsResource fichier inexistant -> 404 Not Found
        ResponseStatusException exNotFound = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.loadFileAsResource("/api/v1/uploads/annonces/non-existent.jpg"));
        assertEquals(404, exNotFound.getStatusCode().value());
    }

    @Test
    @DisplayName("Stockage, chargement et suppression effective d'un fichier réel")
    void testRealFileLifecycle() throws IOException {
        UUID annonceId = UUID.randomUUID();
        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile mockFile = new MockMultipartFile("file", "test_lifecycle.png", "image/png", pngHeader);

        // Store
        String fileUrl = fileStorageService.storePhotoAnnonce(annonceId, mockFile);
        assertNotNull(fileUrl);

        // Load Resource
        org.springframework.core.io.Resource resource = fileStorageService.loadFileAsResource(fileUrl);
        assertTrue(resource.exists());

        // Delete
        boolean deleted = fileStorageService.deleteFile(fileUrl);
        assertTrue(deleted);
    }
}
