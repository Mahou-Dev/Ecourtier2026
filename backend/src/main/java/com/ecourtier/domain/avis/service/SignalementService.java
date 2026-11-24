package com.ecourtier.domain.avis.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.service.AnnonceStatutStateMachine;
import com.ecourtier.domain.avis.dto.SignalementCreateDTO;
import com.ecourtier.domain.avis.dto.SignalementResponseDTO;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SignalementService {

    private final SignalementRepository signalementRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AnnonceStatutStateMachine annonceStatutStateMachine;
    private final TransactionRepository transactionRepository;

    @Transactional
    @CacheEvict(value = "adminStats", allEntries = true)
    public SignalementResponseDTO creerSignalement(UUID annonceId, SignalementCreateDTO dto, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non trouvé"));

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        if (annonce.getUtilisateur() != null && annonce.getUtilisateur().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas signaler votre propre annonce");
        }
        if (signalementRepository.existsByAnnonceIdAndAuteurId(annonceId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà signalé cette annonce");
        }

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
    @CacheEvict(value = "adminStats", allEntries = true)
    public SignalementResponseDTO traiterSignalement(UUID signalementId, TraiterSignalementDTO dto) {
        SignalementEntity signalement = signalementRepository.findById(signalementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Signalement introuvable"));

        if (dto.getStatut() == StatutSignalement.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le statut cible d'une modération doit être TRAITE ou REJETE");
        }
        if (signalement.getStatut() != StatutSignalement.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce signalement a déjà été modéré (statut actuel : " + signalement.getStatut() + ")");
        }
        if (dto.getStatut() == StatutSignalement.REJETE && dto.isSuspendreAnnonce()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de suspendre une annonce lorsque le signalement est rejeté");
        }

        signalement.setStatut(dto.getStatut());

        if (dto.isSuspendreAnnonce()) {
            UUID annonceId = signalement.getAnnonce().getId();
            AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalseForUpdate(annonceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Impossible de suspendre : l'annonce associée est introuvable ou supprimée."
                    ));

            boolean hasActiveBloque = transactionRepository.existsByAnnonceIdAndDossierActifTrueAndStatutSequestreIn(
                    annonce.getId(),
                    List.of(StatutSequestre.BLOQUE)
            );
            annonceStatutStateMachine.appliquerTransitionAdmin(annonce, StatutPublication.SUSPENDUE, hasActiveBloque);
            com.ecourtier.domain.annonce.service.AnnonceService.cloturerInitialiseSurSuspension(
                    transactionRepository,
                    annonce.getId(),
                    "Clôture automatique suite à suspension administrative (signalement " + signalementId + ")"
            );

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
