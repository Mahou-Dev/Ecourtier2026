package com.ecourtier.domain.rendezvous.dto;

import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
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
public class RendezVousResponseDTO {

    private UUID id;
    private UUID annonceId;
    private String annonceTitre;
    private UUID demandeurId;
    private String demandeurNomComplet;
    private String demandeurEmail;
    private LocalDateTime dateHeure;
    private StatutRendezVous statut;
    private String motifAnnulation;
    private Boolean rappelEnvoye;
    private LocalDateTime dateCreation;
}
