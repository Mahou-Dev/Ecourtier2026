package com.ecourtier.domain.utilisateur.mapper;

import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.springframework.stereotype.Component;

@Component
public class UtilisateurMapper {

    public UtilisateurResponseDTO toResponseDTO(UtilisateurEntity entity) {
        if (entity == null) {
            return null;
        }
        return UtilisateurResponseDTO.builder()
                .id(entity.getId())
                .nom(entity.getNom())
                .prenom(entity.getPrenom())
                .email(entity.getEmail())
                .telephone(entity.getTelephone())
                .role(entity.getRole())
                .dateCreation(entity.getDateCreation())
                .actif(entity.isActif())
                .photoProfilUrl(entity.getPhotoProfilUrl())
                .build();
    }
}
