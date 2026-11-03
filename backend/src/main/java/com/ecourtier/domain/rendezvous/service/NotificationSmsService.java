package com.ecourtier.domain.rendezvous.service;

import com.ecourtier.domain.rendezvous.dto.NotificationSmsResponseDTO;
import com.ecourtier.domain.rendezvous.entity.NotificationSmsEntity;
import com.ecourtier.domain.rendezvous.repository.NotificationSmsRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationSmsService {

    private final NotificationSmsRepository notificationSmsRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional(readOnly = true)
    public Page<NotificationSmsResponseDTO> obtenirMesSms(String userEmail, Pageable pageable) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        return notificationSmsRepository.findByUtilisateurIdOrderByDateCreationDesc(user.getId(), pageable)
                .map(this::toDTO);
    }

    private NotificationSmsResponseDTO toDTO(NotificationSmsEntity entity) {
        String annonceTitre = null;
        if (entity.getRendezVous() != null && entity.getRendezVous().getAnnonce() != null) {
            annonceTitre = entity.getRendezVous().getAnnonce().getTitre();
        }
        return NotificationSmsResponseDTO.builder()
                .id(entity.getId())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .telephone(entity.getTelephone())
                .type(entity.getType())
                .message(entity.getMessage())
                .statut(entity.getStatut())
                .rdvId(entity.getRendezVous() != null ? entity.getRendezVous().getId() : null)
                .annonceTitre(annonceTitre)
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
