package com.ecourtier.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MagicBytesValidationTest {

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
    }

    @Test
    @DisplayName("Refus 400 pour un fichier script malveillant renommé en .jpg (Validation Magic Bytes)")
    void testMagicBytesValidationRejectsFakeImage() {
        // Fichier texte/script déguisé avec extension .jpg
        byte[] fakeImageContent = "<?php echo 'malicious code'; ?>".getBytes();
        MockMultipartFile fakeJpg = new MockMultipartFile(
                "file", "hacked.jpg", "image/jpeg", fakeImageContent
        );

        UUID annonceId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fileStorageService.storePhotoAnnonce(annonceId, fakeJpg),
                "Le fichier déguisé avec extension .jpg mais sans magic bytes d'image doit lever une ResponseStatusException (400)");

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Acceptation d'une image PNG valide avec vrais magic bytes PNG (89 50 4E 47)")
    void testMagicBytesValidationAcceptsValidPng() {
        // Headers réels d'une image PNG 1x1 pixel transparent
        byte[] validPngHeader = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
        };

        MockMultipartFile validPng = new MockMultipartFile(
                "file", "test.png", "image/png", validPngHeader
        );

        UUID annonceId = UUID.randomUUID();

        assertDoesNotThrow(() -> fileStorageService.storePhotoAnnonce(annonceId, validPng));
    }
}
