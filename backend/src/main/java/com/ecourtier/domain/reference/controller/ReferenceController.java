package com.ecourtier.domain.reference.controller;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.entity.TypeDocumentVerification;
import com.ecourtier.domain.reference.dto.ReferencesResponseDTO;
import com.ecourtier.domain.reference.dto.ReferencesResponseDTO.ReferenceItemDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/references")
@Tag(name = "Référentiels", description = "Listes de référence dynamiques (types, équipements, proximités, documents)")
public class ReferenceController {

    @GetMapping
    @Operation(summary = "Obtenir toutes les listes de référence", description = "Source unique des listes pour le formulaire, les filtres et les fiches.")
    public ResponseEntity<ReferencesResponseDTO> getReferences() {
        List<ReferenceItemDTO> typesBien = Arrays.stream(TypeBien.values())
                .map(t -> new ReferenceItemDTO(t.name(), getLibelleTypeBien(t), null))
                .toList();

        List<ReferenceItemDTO> equipements = Arrays.stream(Equipement.values())
                .map(e -> new ReferenceItemDTO(e.name(), getLibelleEquipement(e), null))
                .toList();

        List<ReferenceItemDTO> proximites = Arrays.stream(ProximiteTag.values())
                .map(p -> new ReferenceItemDTO(p.name(), getLibelleProximite(p), null))
                .toList();

        List<ReferenceItemDTO> modes = Arrays.stream(ModeTransaction.values())
                .map(m -> new ReferenceItemDTO(m.name(), m == ModeTransaction.LOCATION ? "Location" : "Vente", null))
                .toList();

        List<ReferenceItemDTO> typesDoc = Arrays.stream(TypeDocumentVerification.values())
                .map(d -> new ReferenceItemDTO(d.name(), getLibelleDocument(d), null))
                .toList();

        ReferencesResponseDTO response = ReferencesResponseDTO.builder()
                .typesBien(typesBien)
                .equipements(equipements)
                .proximites(proximites)
                .modesTransaction(modes)
                .typesDocument(typesDoc)
                .build();

        return ResponseEntity.ok(response);
    }

    private String getLibelleTypeBien(TypeBien t) {
        return switch (t) {
            case APPARTEMENT -> "Appartement";
            case VILLA -> "Villa";
            case STUDIO -> "Studio";
        };
    }

    private String getLibelleEquipement(Equipement e) {
        return switch (e) {
            case WIFI -> "Wi-Fi";
            case PARKING -> "Parking";
            case CLIMATISATION -> "Climatisation";
            case PISCINE -> "Piscine";
            case GARDIEN -> "Gardien / Sécurité";
            case GROUPE_ELECTROGENE -> "Groupe électrogène";
            case RESERVE_EAU -> "Réserve d'eau / Cuve";
        };
    }

    private String getLibelleProximite(ProximiteTag p) {
        return switch (p) {
            case ECOLE -> "École";
            case MARCHE -> "Marché";
            case HOPITAL -> "Hôpital / Clinique";
            case TRANSPORT -> "Transports (BRT, Bus)";
            case PLAGE -> "Plage / Mer";
            case COMMERCE -> "Commerces / Supermarché";
            case UNIVERSITE -> "Université (UCAD...)";
        };
    }

    private String getLibelleDocument(TypeDocumentVerification d) {
        return switch (d) {
            case TITRE_FONCIER -> "Titre foncier / Bail";
            case BAIL_PRECEDENT -> "Bail précédent / Facture Senelec";
            case PIECE_IDENTITE -> "Photo du propriétaire avec sa pièce d'identité";
        };
    }
}
