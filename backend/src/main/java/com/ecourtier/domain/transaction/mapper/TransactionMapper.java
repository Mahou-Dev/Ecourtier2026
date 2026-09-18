package com.ecourtier.domain.transaction.mapper;

import com.ecourtier.domain.transaction.dto.TransactionResponseDTO;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponseDTO toResponseDTO(TransactionEntity entity) {
        if (entity == null) return null;

        String locataireNom = entity.getLocataire() != null ?
                (entity.getLocataire().getPrenom() + " " + entity.getLocataire().getNom()).trim() : null;
        String proprietaireNom = entity.getProprietaire() != null ?
                (entity.getProprietaire().getPrenom() + " " + entity.getProprietaire().getNom()).trim() : null;

        return TransactionResponseDTO.builder()
                .id(entity.getId())
                .annonceId(entity.getAnnonce() != null ? entity.getAnnonce().getId() : null)
                .annonceTitre(entity.getAnnonce() != null ? entity.getAnnonce().getTitre() : null)
                .locataireId(entity.getLocataire() != null ? entity.getLocataire().getId() : null)
                .locataireNomComplet(locataireNom)
                .proprietaireId(entity.getProprietaire() != null ? entity.getProprietaire().getId() : null)
                .proprietaireNomComplet(proprietaireNom)
                .montantCaution(entity.getMontantCaution())
                .fraisDossier(entity.getFraisDossier())
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
