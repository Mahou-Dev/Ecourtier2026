package com.ecourtier.domain.avis.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.dto.AvisCreateDTO;
import com.ecourtier.domain.avis.dto.AvisResponseDTO;
import com.ecourtier.domain.avis.dto.AvisSummaryDTO;
import com.ecourtier.domain.avis.dto.ReputationResponseDTO;
import com.ecourtier.domain.avis.entity.AvisEntity;
import com.ecourtier.domain.avis.repository.AvisRepository;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AvisService {

    private final AvisRepository avisRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RendezVousRepository rendezVousRepository;

    @Transactional
    public AvisResponseDTO creerAvis(UUID annonceId, AvisCreateDTO dto, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        if (user.getRole() != Role.LOCATAIRE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seuls les locataires peuvent déposer un avis");
        }

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        // 1. Vérifier si le locataire a eu un RDV EFFECTUE sur cette annonce
        boolean rdvEffectue = rendezVousRepository.existsByAnnonceIdAndDemandeurIdAndStatut(
                annonceId, user.getId(), StatutRendezVous.EFFECTUE
        );

        if (!rdvEffectue) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Seuls les locataires ayant effectué une visite validée sur ce bien peuvent déposer un avis"
            );
        }

        // 2. Vérifier si un avis existe déjà pour cet utilisateur et cette annonce
        if (avisRepository.existsByAnnonceIdAndAuteurId(annonceId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà noté cette annonce");
        }

        AvisEntity entity = AvisEntity.builder()
                .annonce(annonce)
                .auteur(user)
                .note(dto.getNote())
                .commentaire(dto.getCommentaire())
                .build();

        try {
            AvisEntity saved = avisRepository.save(entity);
            return toDTO(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà noté cette annonce");
        }
    }

    @Transactional(readOnly = true)
    public AvisSummaryDTO obtenirAvisAnnonce(UUID annonceId) {
        if (!annonceRepository.existsById(annonceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable");
        }

        List<AvisEntity> list = avisRepository.findByAnnonceIdOrderByDateCreationDesc(annonceId);
        Double avg = avisRepository.findAverageNoteByAnnonceId(annonceId);
        Long count = avisRepository.countByAnnonceId(annonceId);

        List<AvisResponseDTO> dtos = list.stream().map(this::toDTO).toList();

        return AvisSummaryDTO.builder()
                .noteMoyenne(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
                .nombreAvis(count != null ? count : 0L)
                .avis(dtos)
                .build();
    }

    @Transactional(readOnly = true)
    public ReputationResponseDTO obtenirReputationUtilisateur(UUID utilisateurId) {
        if (!utilisateurRepository.existsById(utilisateurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }

        Double avg = avisRepository.findAverageNoteByUtilisateurId(utilisateurId);
        Long count = avisRepository.countByUtilisateurId(utilisateurId);

        return ReputationResponseDTO.builder()
                .utilisateurId(utilisateurId)
                .noteMoyenne(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
                .nombreAvis(count != null ? count : 0L)
                .build();
    }

    private AvisResponseDTO toDTO(AvisEntity entity) {
        return AvisResponseDTO.builder()
                .id(entity.getId())
                .annonceId(entity.getAnnonce().getId())
                .note(entity.getNote())
                .commentaire(entity.getCommentaire())
                .auteurId(entity.getAuteur().getId())
                .auteurPrenom(entity.getAuteur().getPrenom())
                .auteurNom(entity.getAuteur().getNom())
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
