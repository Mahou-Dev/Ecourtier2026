package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnonceResponseDTO {
    private UUID id;
    private String titre;
    private String description;
    private TypeBien type;
    private ModeTransaction modeTransaction;
    private BigDecimal prix;
    private Double superficie;
    private Integer nombrePieces;
    private String quartier;
    private String ville;
    private Double latitude;
    private Double longitude;
    private boolean meuble;
    private boolean animauxAcceptes;
    private String lienVisite360;
    private StatutVerification statutVerification;
    private StatutPublication statutPublication;
    private LocalDateTime dateCreation;

    private UtilisateurResponseDTO auteur;
    private List<PhotoResponseDTO> photos;
    private Set<Equipement> equipements;
    private Set<ProximiteTag> proximites;
    private List<DocumentVerificationResponseDTO> documentsVerification;
}
