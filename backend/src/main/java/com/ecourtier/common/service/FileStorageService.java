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

    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5 MB pour les photos d'annonce
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024; // 10 MB pour les pièces justificatives (images ou PDF)
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final List<String> ALLOWED_DOCUMENT_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp", "pdf");
    private static final String UPLOAD_ROOT_DIR = "uploads";

    public String storePhotoAnnonce(UUID annonceId, MultipartFile file) {
        validatePhotoFile(file);

        try {
            Path targetDir = Paths.get(UPLOAD_ROOT_DIR, "annonces", annonceId.toString());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename).toLowerCase();
            String newFilename = UUID.randomUUID() + "." + extension;

            Path targetPath = targetDir.resolve(newFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/api/v1/uploads/annonces/" + annonceId + "/" + newFilename;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors du stockage du fichier");
        }
    }

    public String storeDocumentVerification(UUID annonceId, MultipartFile file) {
        validateDocumentFile(file);

        try {
            Path targetDir = Paths.get(UPLOAD_ROOT_DIR, "documents", annonceId.toString());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename).toLowerCase();
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
            Path baseDir = Paths.get(UPLOAD_ROOT_DIR).toAbsolutePath().normalize();
            Path filePath = Paths.get(UPLOAD_ROOT_DIR, cleanPath).toAbsolutePath().normalize();

            if (!filePath.startsWith(baseDir)) {
                return false;
            }

            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    public org.springframework.core.io.Resource loadFileAsResource(String relativeUrl) {
        try {
            String cleanPath = relativeUrl.replace("/api/v1/uploads/", "");
            Path baseDir = Paths.get(UPLOAD_ROOT_DIR).toAbsolutePath().normalize();
            Path filePath = Paths.get(UPLOAD_ROOT_DIR, cleanPath).toAbsolutePath().normalize();

            if (!filePath.startsWith(baseDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accès refusé : tentative de path traversal");
            }

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

    private void validatePhotoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier soumis est vide");
        }

        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La taille du fichier dépasse la limite maximale autorisée de 5MB"
            );
        }

        String extension = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Format de fichier non pris en charge pour une photo d'annonce. Formats acceptés : jpg, jpeg, png, webp"
            );
        }

        byte[] header = new byte[12];
        int bytesRead = readHeader(file, header);
        if (bytesRead < 4 || !isImageContentValid(header, bytesRead)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le contenu du fichier est invalide ou corrompu (signature binaire non reconnue)"
            );
        }
    }

    private void validateDocumentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier soumis est vide");
        }

        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La taille du document dépasse la limite maximale autorisée de 10 Mo"
            );
        }

        String extension = getExtension(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Format de document non pris en charge. Formats acceptés : jpg, jpeg, png, webp, pdf"
            );
        }

        byte[] header = new byte[12];
        int bytesRead = readHeader(file, header);
        boolean validContent = "pdf".equals(extension)
                ? isPdfContentValid(header, bytesRead)
                : (bytesRead >= 4 && isImageContentValid(header, bytesRead));

        if (!validContent) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le contenu du document est invalide ou corrompu (signature binaire %PDF- ou image non reconnue)"
            );
        }
    }

    private int readHeader(MultipartFile file, byte[] header) {
        try (var is = file.getInputStream()) {
            return is.read(header);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le contenu du fichier");
        }
    }

    private boolean isImageContentValid(byte[] h, int len) {
        // 1. JPEG: FF D8 FF
        boolean isJpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        // 2. PNG: 89 50 4E 47
        boolean isPng = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        // 3. WEBP: RIFF...WEBP
        boolean isWebp = len >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';

        return isJpeg || isPng || isWebp;
    }

    private boolean isPdfContentValid(byte[] h, int len) {
        // PDF magic bytes: %PDF- (25 50 44 46 2D)
        return len >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-';
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
