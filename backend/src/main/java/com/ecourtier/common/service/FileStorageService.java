package com.ecourtier.common.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final String UPLOAD_ROOT_DIR = "uploads";

    public String storePhotoAnnonce(UUID annonceId, MultipartFile file) {
        validateFile(file);

        try {
            Path targetDir = Paths.get(UPLOAD_ROOT_DIR, "annonces", annonceId.toString());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename);
            String newFilename = UUID.randomUUID() + "." + extension;

            Path targetPath = targetDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/api/v1/uploads/annonces/" + annonceId + "/" + newFilename;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors du stockage du fichier");
        }
    }

    public String storeDocumentVerification(UUID annonceId, MultipartFile file) {
        validateFile(file);

        try {
            Path targetDir = Paths.get(UPLOAD_ROOT_DIR, "documents", annonceId.toString());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename);
            String newFilename = UUID.randomUUID() + "." + extension;

            Path targetPath = targetDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/api/v1/uploads/documents/" + annonceId + "/" + newFilename;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors du stockage du document");
        }
    }

    public boolean deleteFile(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.trim().isEmpty()) {
            return false;
        }
        try {
            String cleanPath = relativeUrl.replace("/api/v1/uploads/", "");
            Path filePath = Paths.get(UPLOAD_ROOT_DIR, cleanPath);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    public org.springframework.core.io.Resource loadFileAsResource(String relativeUrl) {
        try {
            String cleanPath = relativeUrl.replace("/api/v1/uploads/", "");
            Path filePath = Paths.get(UPLOAD_ROOT_DIR, cleanPath).normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier physique introuvable");
            }
        } catch (java.net.MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL du fichier invalide");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier soumis est vide");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La taille du fichier dépasse la limite maximale autorisée de 5MB"
            );
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Format de fichier non pris en charge. Formats acceptés : jpg, jpeg, png, webp"
            );
        }

        // Inspection binaire des magic bytes pour valider le contenu réel du fichier
        try {
            byte[] header = new byte[12];
            int bytesRead;
            try (var is = file.getInputStream()) {
                bytesRead = is.read(header);
            }

            if (bytesRead < 4 || !isContentValid(header, bytesRead)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Le contenu du fichier est invalide ou corrompu (signature binaire non reconnue)"
                );
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le contenu du fichier");
        }
    }

    private boolean isContentValid(byte[] h, int len) {
        // 1. JPEG: FF D8 FF
        boolean isJpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        // 2. PNG: 89 50 4E 47
        boolean isPng = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        // 3. WEBP: RIFF...WEBP
        boolean isWebp = len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';

        return isJpeg || isPng || isWebp;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
