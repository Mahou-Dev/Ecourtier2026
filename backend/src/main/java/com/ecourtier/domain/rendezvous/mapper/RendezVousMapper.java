package com.ecourtier.domain.rendezvous.mapper;

import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import org.springframework.stereotype.Component;

@Component
public class RendezVousMapper {

    public RendezVousResponseDTO toResponseDTO(RendezVousEntity entity) {
        if (entity == null) return null;

        String demandeurNom = entity.getDemandeur() != null ?
                (entity.getDemandeur().getPrenom() + " " + entity.getDemandeur().getNom()).trim() : null;
        String demandeurEmail = entity.getDemandeur() != null ? entity.getDemandeur().getEmail() : null;

        return RendezVousResponseDTO.builder()
                .id(entity.getId())
                .annonceId(entity.getAnnonce() != null ? entity.getAnnonce().getId() : null)
                .annonceTitre(entity.getAnnonce() != null ? entity.getAnnonce().getTitre() : null)
                .demandeurId(entity.getDemandeur() != null ? entity.getDemandeur().getId() : null)
                .demandeurNomComplet(demandeurNom)
                .demandeurEmail(demandeurEmail)
                .dateHeure(entity.getDateHeure())
                .statut(entity.getStatut())
                .motifAnnulation(entity.getMotifAnnulation())
                .rappelEnvoye(entity.getRappelEnvoye())
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
