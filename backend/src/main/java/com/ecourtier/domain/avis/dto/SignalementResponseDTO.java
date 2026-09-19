package com.ecourtier.domain.avis.dto;

import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.StatutSignalement;
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
public class SignalementResponseDTO {
    private UUID id;
    private UUID annonceId;
    private String annonceTitre;
    private UUID auteurId;
    private String auteurEmail;
    private MotifSignalement motif;
    private String description;
    private StatutSignalement statut;
    private LocalDateTime dateCreation;
}
