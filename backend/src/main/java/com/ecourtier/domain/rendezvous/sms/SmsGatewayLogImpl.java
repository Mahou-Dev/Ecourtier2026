package com.ecourtier.domain.rendezvous.sms;

import com.ecourtier.domain.rendezvous.entity.NotificationSmsEntity;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.TypeNotificationSms;
import com.ecourtier.domain.rendezvous.repository.NotificationSmsRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implémentation simulée du SmsGateway qui persiste chaque SMS dans la table
 * notifications_sms (statut = 'SIMULE') et trace un log explicite [SMS SIMULÉ].
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmsGatewayLogImpl implements SmsGateway {

    private final NotificationSmsRepository notificationSmsRepository;

    @Override
    public NotificationSmsEntity envoyerSms(
            UtilisateurEntity destinataire,
            String telephone,
            TypeNotificationSms type,
            String message,
            RendezVousEntity rendezVous
    ) {
        String resolvedPhone = telephone != null ? telephone : (destinataire != null ? destinataire.getTelephone() : null);

        NotificationSmsEntity entity = NotificationSmsEntity.builder()
                .utilisateur(destinataire)
                .telephone(resolvedPhone)
                .type(type)
                .message(message)
                .statut("SIMULE")
                .rendezVous(rendezVous)
                .build();

        NotificationSmsEntity saved = notificationSmsRepository.save(entity);

        log.info("[SMS SIMULÉ] type={} | dest={} (user={}) | rdv={} | message=\"{}\"",
                type,
                resolvedPhone,
                destinataire != null ? destinataire.getEmail() : "N/A",
                rendezVous != null ? rendezVous.getId() : "N/A",
                message);

        return saved;
    }
}
