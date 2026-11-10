package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.TypeBien;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @Size(min = 3, max = 150, message = "Le titre doit comporter entre 3 et 150 caractères")
    private String titre;

    @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
    private String description;

    private TypeBien type;
    private ModeTransaction modeTransaction;

    @Positive(message = "Le prix doit être strictement positif")
    private BigDecimal prix;

    @Positive(message = "La superficie doit être strictement positive")
    private Double superficie;

    @Min(value = 1, message = "Le nombre de pièces doit être d'au moins 1")
    private Integer nombrePieces;

    @Min(value = 0, message = "L'étage ne peut pas être négatif")
    private Integer etage;

    @Size(min = 2, max = 100, message = "Le quartier doit comporter entre 2 et 100 caractères")
    private String quartier;

    @Size(min = 2, max = 100, message = "La ville doit comporter entre 2 et 100 caractères")
    private String ville;

    @DecimalMin(value = "-90.0", message = "La latitude doit être comprise entre -90 et 90")
    @DecimalMax(value = "90.0", message = "La latitude doit être comprise entre -90 et 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "La longitude doit être comprise entre -180 et 180")
    @DecimalMax(value = "180.0", message = "La longitude doit être comprise entre -180 et 180")
    private Double longitude;

    private Boolean meuble;
    private Boolean animauxAcceptes;

    @Schema(description = "Confirmation automatique des demandes de visite sans conflit à ±60 min", example = "true")
    private Boolean confirmationAutomatique;

    @Size(max = 512, message = "Le lien de visite 360 ne doit pas dépasser 512 caractères")
    private String lienVisite360;

    private StatutPublication statutPublication;
    private Set<Equipement> equipements;
    private Set<ProximiteTag> proximites;
}
