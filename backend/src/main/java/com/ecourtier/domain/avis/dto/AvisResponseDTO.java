package com.ecourtier.domain.avis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvisResponseDTO {
    private UUID id;
    private UUID annonceId;
    private Integer note;
    private String commentaire;
    private UUID auteurId;
    private String auteurPrenom;
    private String auteurNom;
    private LocalDateTime dateCreation;
}
