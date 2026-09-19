package com.ecourtier.domain.ia.dto;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.TypeBien;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriteresExtraitsDTO {
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private TypeBien type;
    private ModeTransaction modeTransaction;
    private String quartier;
    private String ville;
    private Integer nombrePieces;
    private Integer etage;
    private Boolean meuble;
    private Boolean animauxAcceptes;
    private Double superficieMin;
    private Double confiance;
    private String questionDeRelance;
}
