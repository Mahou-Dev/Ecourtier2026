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
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
