package com.ecourtier.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "Uploads & Fichiers", description = "Endpoint de consultation et téléchargement des fichiers et images média")
public class UploadController {

    private final Path fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
    private final Path annoncesStorageLocation = fileStorageLocation.resolve("annonces").normalize();

    public UploadController() {
        try {
            if (!Files.exists(annoncesStorageLocation)) {
                Files.createDirectories(annoncesStorageLocation);
            }
        } catch (IOException ignored) {}
    }

    @GetMapping("/annonces/{id}/{filename:.+}")
    @Operation(summary = "Consulter une photo d'annonce téléversée", description = "Servir une photo d'annonce publique.")
    public ResponseEntity<Resource> getPhoto(
            @PathVariable String id,
            @PathVariable String filename
    ) {
        if (!isSafePathSegment(id) || !isSafePathSegment(filename)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path annonceDir = annoncesStorageLocation.resolve(id).normalize();
            if (!annonceDir.startsWith(annoncesStorageLocation) || annonceDir.equals(annoncesStorageLocation)) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = annonceDir.resolve(filename).normalize();
            if (!filePath.startsWith(annonceDir) || !annonceDir.equals(filePath.getParent())) {
                return ResponseEntity.badRequest().build();
            }

            // Vérification anti-liens symboliques explicite
            if (Files.isSymbolicLink(filePath) || Files.isSymbolicLink(annonceDir)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
                return ResponseEntity.notFound().build();
            }

            // Vérification du chemin réel canonique (toRealPath)
            Path realFilePath;
            Path realRootLocation;
            try {
                realFilePath = filePath.toRealPath();
                realRootLocation = annoncesStorageLocation.toRealPath();
            } catch (IOException ioEx) {
                return ResponseEntity.notFound().build();
            }

            if (!realFilePath.startsWith(realRootLocation)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.isRegularFile(realFilePath, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(realFilePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(realFilePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = getContentType(filename);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException | InvalidPathException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean isSafePathSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        return !segment.contains("..")
                && !segment.contains("/")
                && !segment.contains("\\")
                && !segment.contains(":")
                && segment.indexOf('\0') < 0;
    }

    private String getContentType(String filename) {
        if (filename.toLowerCase().endsWith(".png")) return "image/png";
        if (filename.toLowerCase().endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
