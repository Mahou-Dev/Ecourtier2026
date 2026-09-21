package com.ecourtier.domain.annonce.dto;

import com.ecourtier.domain.annonce.entity.Equipement;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.ProximiteTag;
import com.ecourtier.domain.annonce.entity.TypeBien;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class AnnonceCreateDTO {

    @NotBlank(message = "Le titre est obligatoire")
    private String titre;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotNull(message = "Le type de bien est obligatoire (STUDIO, APPARTEMENT, VILLA)")
    private TypeBien type;

    @NotNull(message = "Le mode de transaction est obligatoire (LOCATION, VENTE)")
    private ModeTransaction modeTransaction;

    @NotNull(message = "Le prix est obligatoire")
    @Positive(message = "Le prix doit être positif")
    private BigDecimal prix;

    @NotNull(message = "La superficie est obligatoire")
    @Positive(message = "La superficie doit être positive")
    private Double superficie;

    @NotNull(message = "Le nombre de pièces est obligatoire")
    @Min(value = 1, message = "Le nombre de pièces doit être d'au moins 1")
    private Integer nombrePieces;

    private Integer etage;

    @NotBlank(message = "Le quartier est obligatoire")
    private String quartier;

    @NotBlank(message = "La ville est obligatoire")
    private String ville;

    @NotNull(message = "La latitude est obligatoire")
    private Double latitude;

    @NotNull(message = "La longitude est obligatoire")
    private Double longitude;

    private boolean meuble;
    private boolean animauxAcceptes;
    private String lienVisite360;

    private Set<Equipement> equipements;
    private Set<ProximiteTag> proximites;
}
