package com.ecourtier.domain.avis.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.dto.SignalementCreateDTO;
import com.ecourtier.domain.avis.dto.SignalementResponseDTO;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignalementService {

    private final SignalementRepository signalementRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional
    public SignalementResponseDTO creerSignalement(UUID annonceId, SignalementCreateDTO dto, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        SignalementEntity entity = SignalementEntity.builder()
                .annonce(annonce)
                .auteur(user)
                .motif(dto.getMotif())
                .description(dto.getDescription())
                .statut(StatutSignalement.EN_ATTENTE)
                .build();

        SignalementEntity saved = signalementRepository.save(entity);
        log.info("[SIGNALEMENT] Nouveau signalement {} créé pour l'annonce {} par {}", saved.getId(), annonceId, userEmail);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<SignalementResponseDTO> listerSignalements(StatutSignalement statut, Pageable pageable) {
        Page<SignalementEntity> page = (statut != null)
                ? signalementRepository.findByStatut(statut, pageable)
                : signalementRepository.findAll(pageable);
        return page.map(this::toDTO);
    }

    @Transactional
    public SignalementResponseDTO traiterSignalement(UUID signalementId, TraiterSignalementDTO dto) {
        SignalementEntity signalement = signalementRepository.findById(signalementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Signalement introuvable"));

        signalement.setStatut(dto.getStatut());

        if (dto.isSuspendreAnnonce()) {
            AnnonceEntity annonce = signalement.getAnnonce();
            annonce.setStatutPublication(StatutPublication.SUSPENDUE);
            annonceRepository.save(annonce);
            log.info("[SIGNALEMENT] Annonce {} suspendue suite au traitement du signalement {}", annonce.getId(), signalementId);
        }

        SignalementEntity updated = signalementRepository.save(signalement);
        return toDTO(updated);
    }

    private SignalementResponseDTO toDTO(SignalementEntity entity) {
        return SignalementResponseDTO.builder()
                .id(entity.getId())
                .annonceId(entity.getAnnonce().getId())
                .annonceTitre(entity.getAnnonce().getTitre())
                .auteurId(entity.getAuteur().getId())
                .auteurEmail(entity.getAuteur().getEmail())
                .motif(entity.getMotif())
                .description(entity.getDescription())
                .statut(entity.getStatut())
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
