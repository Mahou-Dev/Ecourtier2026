package com.ecourtier.domain.transaction.mapper;

import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionMapper {

    @Value("${ecourtier.vente.acompte-pourcentage:10}")
    private int acomptePourcentage = 10;

    public TransactionResponseDTO toResponseDTO(TransactionEntity entity) {
        if (entity == null) return null;

        String locataireNom = entity.getLocataire() != null ?
                (entity.getLocataire().getPrenom() + " " + entity.getLocataire().getNom()).trim() : null;
        String proprietaireNom = entity.getProprietaire() != null ?
                (entity.getProprietaire().getPrenom() + " " + entity.getProprietaire().getNom()).trim() : null;

        BigDecimal montantCaution = entity.getMontantCaution() != null ? entity.getMontantCaution() : BigDecimal.ZERO;
        BigDecimal montantPremierLoyer = entity.getMontantPremierLoyer() != null ? entity.getMontantPremierLoyer() : BigDecimal.ZERO;
        BigDecimal fraisDossier = entity.getFraisDossier() != null ? entity.getFraisDossier() : BigDecimal.ZERO;
        BigDecimal montantTotalSequestre = entity.getMontantTotalSequestre();
        BigDecimal montantVerseProprietaire = entity.getMontantVerseProprietaire();
        ModeTransaction mode = entity.getAnnonce() != null ? entity.getAnnonce().getModeTransaction() : null;

        return TransactionResponseDTO.builder()
                .id(entity.getId())
                .annonceId(entity.getAnnonce() != null ? entity.getAnnonce().getId() : null)
                .annonceTitre(entity.getAnnonce() != null ? entity.getAnnonce().getTitre() : null)
                .modeTransaction(mode)
                .annoncePrix(entity.getAnnonce() != null ? entity.getAnnonce().getPrix() : null)
                .acomptePourcentage(mode == ModeTransaction.VENTE ? acomptePourcentage : null)
                .locataireId(entity.getLocataire() != null ? entity.getLocataire().getId() : null)
                .locataireNomComplet(locataireNom)
                .proprietaireId(entity.getProprietaire() != null ? entity.getProprietaire().getId() : null)
                .proprietaireNomComplet(proprietaireNom)
                .montantCaution(montantCaution)
                .montantPremierLoyer(montantPremierLoyer)
                .fraisDossier(fraisDossier)
                .montantTotalSequestre(montantTotalSequestre)
                .montantVerseProprietaire(montantVerseProprietaire)
                .statutSequestre(entity.getStatutSequestre())
                .referenceWave(entity.getReferenceWave())
                .statutBail(entity.getStatutBail())
                .dateSignatureLocataire(entity.getDateSignatureLocataire())
                .dateSignatureProprietaire(entity.getDateSignatureProprietaire())
                .dateLiberation(entity.getDateLiberation())
                .pdfBailPath(entity.getPdfBailPath())
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
