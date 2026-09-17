package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.TypeBien;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnonceUpdateDTO {
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
    private Boolean meuble;
    private Boolean animauxAcceptes;
    private String lienVisite360;
    private StatutPublication statutPublication;
    private Set<Equipement> equipements;
    private Set<ProximiteTag> proximites;
}
