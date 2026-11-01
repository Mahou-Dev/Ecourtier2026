package com.ecourtier.domain.rendezvous.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.dto.RendezVousAnnulationDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousRequestDTO;
import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.entity.TypeNotificationSms;
import com.ecourtier.domain.rendezvous.mapper.RendezVousMapper;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.rendezvous.sms.SmsGateway;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RendezVousService {

    private static final ZoneId DAKAR_ZONE = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_HEURE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final RendezVousRepository rendezVousRepository;
    private final AnnonceRepository annonceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RendezVousMapper rendezVousMapper;
    private final SmsGateway smsGateway;
    private final java.time.Clock clock;

    @Transactional
    public RendezVousResponseDTO demanderRendezVous(UUID annonceId, RendezVousRequestDTO request, String userEmail) {
        UtilisateurEntity user = utilisateurRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));

        AnnonceEntity annonce = annonceRepository.findByIdAndSupprimeFalse(annonceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable"));

        if (annonce.getStatutPublication() != StatutPublication.DISPONIBLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce bien n'est plus disponible");
        }

        LocalDateTime dateHeure = request.getDateHeure();
        boolean autoConfirme = false;
        if (annonce.isConfirmationAutomatique() && dateHeure != null) {
            List<RendezVousEntity> conflicts = rendezVousRepository.findConfirmedInWindow(
                    annonceId,
                    dateHeure.minusMinutes(60),
                    dateHeure.plusMinutes(60)
            );
            autoConfirme = conflicts.isEmpty();
        }

        StatutRendezVous statutInitial = autoConfirme ? StatutRendezVous.CONFIRME : StatutRendezVous.DEMANDE;

        RendezVousEntity rdv = RendezVousEntity.builder()
                .annonce(annonce)
                .demandeur(user)
                .dateHeure(dateHeure)
                .statut(statutInitial)
                .rappelEnvoye(false)
                .build();

        RendezVousEntity saved = rendezVousRepository.save(rdv);

        String dateStr = formatDateHeure(saved.getDateHeure());
        String titreAnnonce = annonce.getTitre();

        // SMS demande au locataire (« demande enregistrée ») et au propriétaire (« nouvelle demande »)
        envoyerSmsSecurise(
                user,
                TypeNotificationSms.RDV_DEMANDE,
                String.format("E-Courtier : votre demande enregistrée pour la visite de \"%s\" le %s.", titreAnnonce, dateStr),
                saved
        );

        UtilisateurEntity proprietaire = annonce.getUtilisateur();
        envoyerSmsSecurise(
                proprietaire,
                TypeNotificationSms.RDV_DEMANDE,
                String.format("E-Courtier : nouvelle demande de visite de %s %s pour \"%s\" le %s%s.",
                        user.getPrenom(),
                        user.getNom(),
                        titreAnnonce,
                        dateStr,
                        autoConfirme ? " (confirmée automatiquement)" : ""),
                saved
        );

        if (autoConfirme) {
            envoyerSmsSecurise(
                    user,
                    TypeNotificationSms.RDV_CONFIRME,
                    String.format("E-Courtier : votre visite pour \"%s\" prévue le %s est confirmée automatiquement.", titreAnnonce, dateStr),
                    saved
            );
        }

        return rendezVousMapper.toResponseDTO(saved);
    }

    @Transactional
    public RendezVousResponseDTO confirmerRendezVous(UUID rdvId, String userEmail) {
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable"));

        // Vérification anti-chevauchement : aucun autre RDV CONFIRME à ±60 min
        LocalDateTime startSlot = rdv.getDateHeure().minusMinutes(60);
        LocalDateTime endSlot = rdv.getDateHeure().plusMinutes(60);

        List<RendezVousEntity> conflicts = rendezVousRepository.findConflictingConfirmedSlot(
                rdv.getAnnonce().getId(),
                rdv.getId(),
                startSlot,
                endSlot
        );

        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un rendez-vous est déjà confirmé sur ce créneau horaire (± 60 min) pour cette annonce"
            );
        }

        rdv.setStatut(StatutRendezVous.CONFIRME);
        RendezVousEntity saved = rendezVousRepository.save(rdv);

        String dateStr = formatDateHeure(saved.getDateHeure());
        String titreAnnonce = saved.getAnnonce() != null ? saved.getAnnonce().getTitre() : "";
        envoyerSmsSecurise(
                saved.getDemandeur(),
                TypeNotificationSms.RDV_CONFIRME,
                String.format("E-Courtier : votre visite pour \"%s\" prévue le %s a été confirmée par le propriétaire.", titreAnnonce, dateStr),
                saved
        );

        return rendezVousMapper.toResponseDTO(saved);
    }

    @Transactional
    public RendezVousResponseDTO annulerRendezVous(UUID rdvId, RendezVousAnnulationDTO request, String userEmail) {
        RendezVousEntity rdv = rendezVousRepository.findById(rdvId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable"));

        String motif = request != null ? request.getMotif() : null;
        rdv.setStatut(StatutRendezVous.ANNULE);
        rdv.setMotifAnnulation(motif);
        RendezVousEntity saved = rendezVousRepository.save(rdv);

        UtilisateurEntity demandeur = saved.getDemandeur();
        UtilisateurEntity proprietaire = saved.getAnnonce() != null ? saved.getAnnonce().getUtilisateur() : null;
        String dateStr = formatDateHeure(saved.getDateHeure());
        String titreAnnonce = saved.getAnnonce() != null ? saved.getAnnonce().getTitre() : "";
        String suffixeMotif = (motif != null && !motif.isBlank()) ? " (motif : " + motif + ")" : "";

        boolean annuleParDemandeur = demandeur != null && demandeur.getEmail() != null
                && demandeur.getEmail().equalsIgnoreCase(userEmail);
        boolean annuleParProprietaire = proprietaire != null && proprietaire.getEmail() != null
                && proprietaire.getEmail().equalsIgnoreCase(userEmail);

        if (annuleParDemandeur) {
            envoyerSmsSecurise(
                    proprietaire,
                    TypeNotificationSms.RDV_ANNULE,
                    String.format("E-Courtier : le rendez-vous de visite pour \"%s\" du %s a été annulé par le demandeur%s.", titreAnnonce, dateStr, suffixeMotif),
                    saved
            );
        } else if (annuleParProprietaire) {
            envoyerSmsSecurise(
                    demandeur,
                    TypeNotificationSms.RDV_ANNULE,
                    String.format("E-Courtier : votre rendez-vous de visite pour \"%s\" du %s a été annulé par le propriétaire%s.", titreAnnonce, dateStr, suffixeMotif),
                    saved
            );
        } else {
            // Annulation administrative : notifier le demandeur (et le propriétaire s'il est distinct)
            envoyerSmsSecurise(
                    demandeur,
                    TypeNotificationSms.RDV_ANNULE,
                    String.format("E-Courtier : le rendez-vous de visite pour \"%s\" du %s a été annulé%s.", titreAnnonce, dateStr, suffixeMotif),
                    saved
            );
            if (proprietaire != null && (demandeur == null || !proprietaire.getId().equals(demandeur.getId()))) {
                envoyerSmsSecurise(
                        proprietaire,
                        TypeNotificationSms.RDV_ANNULE,
                        String.format("E-Courtier : le rendez-vous de visite pour \"%s\" du %s a été annulé%s.", titreAnnonce, dateStr, suffixeMotif),
                        saved
                );
            }
        }

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

    @Scheduled(cron = "${ecourtier.rdv.rappel-cron:0 0 9 * * *}", zone = "Africa/Dakar")
    @Transactional
    public int simulerRappelsDemain() {
        LocalDate tomorrow = LocalDate.now(clock != null ? clock.withZone(DAKAR_ZONE) : java.time.Clock.system(DAKAR_ZONE)).plusDays(1);
        LocalDateTime startTomorrow = tomorrow.atStartOfDay();
        LocalDateTime endTomorrow = tomorrow.atTime(23, 59, 59);

        List<RendezVousEntity> rdvsDemain = rendezVousRepository.findByStatutAndRappelEnvoyeFalseAndDateHeureBetween(
                StatutRendezVous.CONFIRME,
                startTomorrow,
                endTomorrow
        );

        int envoyes = 0;
        for (RendezVousEntity rdv : rdvsDemain) {
            String dateStr = formatDateHeure(rdv.getDateHeure());
            String titreAnnonce = rdv.getAnnonce() != null ? rdv.getAnnonce().getTitre() : "";
            boolean ok = envoyerSmsSecurise(
                    rdv.getDemandeur(),
                    TypeNotificationSms.RDV_RAPPEL,
                    String.format("E-Courtier [Rappel] : votre visite pour \"%s\" est prévue demain (%s).", titreAnnonce, dateStr),
                    rdv
            );
            if (ok) {
                rdv.setRappelEnvoye(true);
                rendezVousRepository.save(rdv);
                envoyes++;
            }
        }

        return envoyes;
    }

    private boolean envoyerSmsSecurise(
            UtilisateurEntity destinataire,
            TypeNotificationSms type,
            String message,
            RendezVousEntity rdv
    ) {
        if (destinataire == null) {
            return false;
        }
        try {
            smsGateway.envoyerSms(destinataire, destinataire.getTelephone(), type, message, rdv);
            return true;
        } catch (Exception e) {
            log.error("[SMS SIMULÉ] Échec d'envoi SMS ({}) pour le RDV {} — ignoré pour ne pas faire échouer le RDV : {}",
                    type,
                    rdv != null ? rdv.getId() : "N/A",
                    e.getMessage());
            return false;
        }
    }

    private String formatDateHeure(LocalDateTime dt) {
        return dt != null ? dt.format(DATE_HEURE_FMT) : "date non précisée";
    }
}
