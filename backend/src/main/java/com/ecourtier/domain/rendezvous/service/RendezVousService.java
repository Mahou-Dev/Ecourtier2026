package com.ecourtier.domain.rendezvous.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.dto.RendezVousAnnulationDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousRequestDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.mapper.RendezVousMapper;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RendezVousService {

    private final RendezVousRepository rendezVousRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RendezVousMapper rendezVousMapper;

    @Transactional
    public RendezVousResponseDTO demanderRendezVous(UUID annonceId, RendezVousRequestDTO request, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        if (annonce.getStatutPublication() != StatutPublication.DISPONIBLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce bien n'est plus disponible");
        }

        RendezVousEntity rdv = RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(user)
                .dateHeure(request.getDateHeure())
                .statut(StatutRendezVous.DEMANDE)
                .rappelEnvoye(false)
                .build();

        RendezVousEntity saved = rendezVousRepository.save(rdv);
        return rendezVousMapper.toResponseDTO(saved);
    }

    @Transactional
    public RendezVousResponseDTO confirmerRendezVous(UUID rdvId, String userEmail) {
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable"));

        // Double booking check: Check if another CONFIRME RDV exists for this listing within +- 1 hour
        LocalDateTime startSlot = rdv.getDateHeure().minusMinutes(59);
        LocalDateTime endSlot = rdv.getDateHeure().plusMinutes(59);

        List<RendezVousEntity> conflicts = rendezVousRepository.findConflictingConfirmedSlot(
                rdv.getAnnonce().getId(),
                rdv.getId(),
                startSlot,
                endSlot
        );

        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un rendez-vous est déjà confirmé sur ce créneau horaire (± 1h) pour cette annonce"
            );
        }

        rdv.setStatut(StatutRendezVous.CONFIRME);
        RendezVousEntity saved = rendezVousRepository.save(rdv);
        return rendezVousMapper.toResponseDTO(saved);
    }

    @Transactional
    public RendezVousResponseDTO annulerRendezVous(UUID rdvId, RendezVousAnnulationDTO request, String userEmail) {
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable"));

        rdv.setStatut(StatutRendezVous.ANNULE);
        rdv.setMotifAnnulation(request != null ? request.getMotif() : null);
        RendezVousEntity saved = rendezVousRepository.save(rdv);
        return rendezVousMapper.toResponseDTO(saved);
    }

    @Transactional
    public RendezVousResponseDTO marquerEffectue(UUID rdvId, String userEmail) {
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable"));

        rdv.setStatut(StatutRendezVous.EFFECTUE);
        RendezVousEntity saved = rendezVousRepository.save(rdv);
        return rendezVousMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> obtenirMesRendezVous(String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        List<RendezVousEntity> rdvs = rendezVousRepository.findByDemandeurIdOrAnnonceUtilisateurIdOrderByDateHeureDesc(
                user.getId(),
                user.getId()
        );

        return rdvs.stream().map(rendezVousMapper::toResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<RendezVousResponseDTO> obtenirRendezVousAnnonce(UUID annonceId, String userEmail) {
        List<RendezVousEntity> rdvs = rendezVousRepository.findByAnnonceIdOrderByDateHeureAsc(annonceId);
        return rdvs.stream().map(rendezVousMapper::toResponseDTO).toList();
    }

    @Scheduled(cron = "0 0 8 * * *") // Exécution tous les jours à 8h00
    @Transactional
    public int simulerRappelsDemain() {
        LocalDateTime startTomorrow = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endTomorrow = LocalDateTime.now().plusDays(1).withHour(23).withMinute(59).withSecond(59);

        List<RendezVousEntity> rdvsDemain = rendezVousRepository.findByStatutAndRappelEnvoyeFalseAndDateHeureBetween(
                StatutRendezVous.CONFIRME,
                startTomorrow,
                endTomorrow
        );

        for (RendezVousEntity rdv : rdvsDemain) {
            log.info("[SIMULATION SMS RAPPEL RDV] Notification envoyée au demandeur {} ({}) pour la visite de l'annonce '{}' prévue le {}",
                    rdv.getDemandeur().getPrenom() + " " + rdv.getDemandeur().getNom(),
                    rdv.getDemandeur().getTelephone(),
                    rdv.getAnnonce().getTitre(),
                    rdv.getDateHeure()
            );
            rdv.setRappelEnvoye(true);
            rendezVousRepository.save(rdv);
        }

        return rdvsDemain.size();
    }
}
