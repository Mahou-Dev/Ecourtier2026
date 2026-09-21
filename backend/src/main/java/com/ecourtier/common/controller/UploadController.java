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

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/uploads")
@Tag(name = "Uploads & Fichiers", description = "Endpoint de consultation et téléchargement des fichiers et images média")
public class UploadController {

    private final Path fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();

    @GetMapping("/annonces/{id}/{filename:.+}")
    @Operation(summary = "Consulter une photo d'annonce téléversée", description = "Servir une photo d'annonce publique.")
    public ResponseEntity<Resource> getPhoto(
            @PathVariable String id,
            @PathVariable String filename
    ) {
        try {
            Path filePath = fileStorageLocation.resolve("annonces").resolve(id).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = getContentType(filename);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String getContentType(String filename) {
        if (filename.toLowerCase().endsWith(".png")) return "image/png";
        if (filename.toLowerCase().endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
