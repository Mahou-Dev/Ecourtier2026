package com.ecourtier.domain.transaction.dto;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponseDTO {

    private UUID id;
    private UUID annonceId;
    private String annonceTitre;

    @Schema(description = "Mode de transaction de l'annonce (LOCATION ou VENTE)")
    private ModeTransaction modeTransaction;

    @Schema(description = "Prix affiché sur l'annonce (loyer mensuel ou prix de vente)")
    private BigDecimal annoncePrix;

    @Schema(description = "Pourcentage d'acompte appliqué en mode VENTE (ex: 10)")
    private Integer acomptePourcentage;

    private UUID locataireId;
    private String locataireNomComplet;
    private UUID proprietaireId;
    private String proprietaireNomComplet;
    private BigDecimal montantCaution;
    private BigDecimal montantPremierLoyer;
    private BigDecimal fraisDossier;
    private BigDecimal montantTotalSequestre;
    private BigDecimal montantVerseProprietaire;
    private StatutSequestre statutSequestre;
    private String referenceWave;
    private StatutBail statutBail;
    private LocalDateTime dateSignatureLocataire;
    private LocalDateTime dateSignatureProprietaire;
    private LocalDateTime dateLiberation;
    private String pdfBailPath;
    private LocalDateTime dateCreation;
}
