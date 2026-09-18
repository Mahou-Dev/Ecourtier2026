package com.ecourtier.domain.transaction.dto;

import com.ecourtier.domain.transaction.entity.StatutBail;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
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
    private UUID locataireId;
    private String locataireNomComplet;
    private UUID proprietaireId;
    private String proprietaireNomComplet;
    private BigDecimal montantCaution;
    private BigDecimal fraisDossier;
    private StatutSequestre statutSequestre;
    private String referenceWave;
    private StatutBail statutBail;
    private LocalDateTime dateSignatureLocataire;
    private LocalDateTime dateSignatureProprietaire;
    private LocalDateTime dateLiberation;
    private String pdfBailPath;
    private LocalDateTime dateCreation;
}
