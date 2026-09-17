package com.ecourtier.domain.annonce.mapper;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.dto.DocumentVerificationResponseDTO;
import com.ecourtier.domain.annonce.dto.PhotoResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.DocumentVerificationEntity;
import com.ecourtier.domain.annonce.entity.PhotoAnnonceEntity;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AnnonceMapper {

    private final UtilisateurMapper utilisateurMapper;

    public AnnonceResponseDTO toResponseDTO(AnnonceEntity entity) {
        if (entity == null) {
            return null;
        }

        List<PhotoResponseDTO> photos = entity.getPhotos() == null ? List.of() :
                entity.getPhotos().stream()
                        .sorted(Comparator.comparingInt(PhotoAnnonceEntity::getOrdre))
                        .map(this::toPhotoDTO)
                        .toList();

        List<DocumentVerificationResponseDTO> docs = entity.getDocumentsVerification() == null ? List.of() :
                entity.getDocumentsVerification().stream()
                        .map(this::toDocumentDTO)
                        .toList();

        return AnnonceResponseDTO.builder()
                .id(entity.getId())
                .titre(entity.getTitre())
                .description(entity.getDescription())
                .type(entity.getType())
                .modeTransaction(entity.getModeTransaction())
                .prix(entity.getPrix())
                .superficie(entity.getSuperficie())
                .nombrePieces(entity.getNombrePieces())
                .quartier(entity.getQuartier())
                .ville(entity.getVille())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .meuble(entity.isMeuble())
                .animauxAcceptes(entity.isAnimauxAcceptes())
                .lienVisite360(entity.getLienVisite360())
                .statutVerification(entity.getStatutVerification())
                .statutPublication(entity.getStatutPublication())
                .dateCreation(entity.getDateCreation())
                .auteur(utilisateurMapper.toResponseDTO(entity.getUtilisateur()))
                .photos(photos)
                .equipements(entity.getEquipements())
                .proximites(entity.getProximites())
                .documentsVerification(docs)
                .build();
    }

    public PhotoResponseDTO toPhotoDTO(PhotoAnnonceEntity entity) {
        if (entity == null) return null;
        return PhotoResponseDTO.builder()
                .id(entity.getId())
                .url(entity.getUrl())
                .ordre(entity.getOrdre())
                .build();
    }

    public DocumentVerificationResponseDTO toDocumentDTO(DocumentVerificationEntity entity) {
        if (entity == null) return null;
        return DocumentVerificationResponseDTO.builder()
                .id(entity.getId())
                .typeDocument(entity.getTypeDocument())
                .url(entity.getUrl())
                .statut(entity.getStatut())
                .motifRejet(entity.getMotifRejet())
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
