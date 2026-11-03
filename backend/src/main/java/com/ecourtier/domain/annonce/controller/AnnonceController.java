package com.ecourtier.domain.annonce.controller;

import com.ecourtier.domain.annonce.dto.AnnonceCreateDTO;
import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.dto.AnnonceUpdateDTO;
import com.ecourtier.domain.annonce.dto.DocumentVerificationResponseDTO;
import com.ecourtier.domain.annonce.dto.OrdrePhotosDTO;
import com.ecourtier.domain.annonce.dto.PhotoResponseDTO;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.entity.TypeDocumentVerification;
import com.ecourtier.domain.annonce.service.AnnonceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/annonces")
@RequiredArgsConstructor
@Tag(name = "Annonces Immobilières", description = "Endpoints de gestion, recherche et publication d'annonces")
public class AnnonceController {

    private final AnnonceService annonceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PROPRIETAIRE', 'AGENT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Créer une nouvelle annonce", description = "Création d'une annonce immobilière (réservée aux propriétaires et agents agréés).")
    public ResponseEntity<AnnonceResponseDTO> creerAnnonce(
            @Valid @RequestBody AnnonceCreateDTO request,
            Authentication authentication
    ) {
        AnnonceResponseDTO response = annonceService.creerAnnonce(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter le détail d'une annonce", description = "Accès public sans authentification préalable.")
    public ResponseEntity<AnnonceResponseDTO> obtenirAnnonceParId(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String userEmail = authentication != null ? authentication.getName() : null;
        AnnonceResponseDTO response = annonceService.obtenirAnnonceParId(id, userEmail);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Modifier une annonce", description = "Réservé à l'auteur de l'annonce ou à un administrateur.")
    public ResponseEntity<AnnonceResponseDTO> modifierAnnonce(
            @PathVariable UUID id,
            @RequestBody AnnonceUpdateDTO request
    ) {
        AnnonceResponseDTO response = annonceService.modifierAnnonce(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Supprimer une annonce (Soft delete)", description = "Suppression logique réservée à l'auteur ou à un administrateur.")
    public ResponseEntity<Void> supprimerAnnonceLogique(@PathVariable UUID id) {
        annonceService.supprimerAnnonceLogique(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/quartiers")
    @Operation(summary = "Liste des quartiers distincts des annonces disponibles", description = "Accès public pour l'autocomplétion.")
    public ResponseEntity<List<String>> obtenirQuartiersDisponibles() {
        return ResponseEntity.ok(annonceService.obtenirQuartiersDisponibles());
    }

    @GetMapping
    @Operation(summary = "Recherche paginée d'annonces avec filtres avancés", description = "Accès public avec critères multiples (budget, superficie, ville, meublé, proximité, etc.).")
    public ResponseEntity<Page<AnnonceResponseDTO>> rechercherAnnonces(
            @PageableDefault(size = 12, sort = "dateCreation", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) BigDecimal budgetMin,
            @RequestParam(required = false) BigDecimal budgetMax,
            @RequestParam(name = "type", required = false) List<TypeBien> types,
            @RequestParam(required = false) ModeTransaction modeTransaction,
            @RequestParam(required = false) Double superficieMin,
            @RequestParam(required = false) Integer nombrePieces,
            @RequestParam(required = false) String quartier,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) Boolean meuble,
            @RequestParam(required = false) Boolean animauxAcceptes,
            @RequestParam(required = false) StatutVerification statutVerification,
            @RequestParam(name = "proximite", required = false) List<ProximiteTag> proximites,
            @RequestParam(name = "include_indisponibles", required = false, defaultValue = "false") Boolean includeIndisponibles,
            @RequestParam(name = "mes_annonces", required = false, defaultValue = "false") Boolean mesAnnonces,
            @RequestParam(name = "mes-annonces", required = false, defaultValue = "false") Boolean mesAnnoncesDash,
            Authentication authentication
    ) {
        String auteurEmail = null;
        if ((Boolean.TRUE.equals(mesAnnonces) || Boolean.TRUE.equals(mesAnnoncesDash)) && authentication != null) {
            auteurEmail = authentication.getName();
        }

        String userEmail = authentication != null ? authentication.getName() : null;

        Page<AnnonceResponseDTO> result = annonceService.rechercherAnnonces(
                pageable, budgetMin, budgetMax, types, modeTransaction,
                superficieMin, nombrePieces, quartier, ville, meuble,
                animauxAcceptes, statutVerification, proximites, includeIndisponibles, auteurEmail, userEmail
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Téléverser des photos", description = "Upload de 1 à plusieurs photos (max 10 photos par annonce, max 5MB par fichier).")
    public ResponseEntity<List<PhotoResponseDTO>> ajouterPhotos(
            @PathVariable UUID id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<PhotoResponseDTO> response = annonceService.ajouterPhotos(id, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Supprimer une photo", description = "Supprime la photo physiquement et en base de données.")
    public ResponseEntity<Void> supprimerPhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId
    ) {
        annonceService.supprimerPhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/photos/ordre")
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Réordonner les photos d'une annonce", description = "Mise à jour de l'ordre d'affichage des photos.")
    public ResponseEntity<List<PhotoResponseDTO>> reordonnerPhotos(
            @PathVariable UUID id,
            @Valid @RequestBody OrdrePhotosDTO dto
    ) {
        List<PhotoResponseDTO> response = annonceService.reordonnerPhotos(id, dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Soumettre un document justificatif pour vérification", description = "Téléverser un document (titre foncier, bail précédent, photo du propriétaire avec sa pièce d'identité) au format image ou PDF (max 10 Mo) pour l'attribution du badge vérifié.")
    public ResponseEntity<DocumentVerificationResponseDTO> ajouterDocumentVerification(
            @PathVariable UUID id,
            @RequestParam("typeDocument") TypeDocumentVerification typeDocument,
            @RequestPart("file") MultipartFile file
    ) {
        DocumentVerificationResponseDTO response = annonceService.ajouterDocumentVerification(id, typeDocument, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/documents/{documentId}/fichier")
    @PreAuthorize("@annonceSecurityService.isOwnerOrAdmin(#id, authentication)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Télécharger / consulter un document de vérification", description = "Accès sécurisé réservé à l'auteur de l'annonce ou un ADMIN. Retourne le flux binaire avec son type MIME (image ou application/pdf).")
    public ResponseEntity<org.springframework.core.io.Resource> obtenirFichierDocumentVerification(
            @PathVariable UUID id,
            @PathVariable UUID documentId
    ) {
        org.springframework.core.io.Resource resource = annonceService.obtenirFichierDocumentVerification(id, documentId);
        String filename = resource.getFilename() != null ? resource.getFilename() : "document";
        String lower = filename.toLowerCase();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (lower.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else if (lower.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            mediaType = MediaType.IMAGE_JPEG;
        } else if (lower.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
