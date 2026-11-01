package com.ecourtier.domain.rendezvous.sms;

import com.ecourtier.domain.rendezvous.entity.NotificationSmsEntity;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.TypeNotificationSms;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;

/**
 * Port d'envoi de notifications SMS (substituable par un fournisseur réel en production,
 * selon le même principe d'abstraction que WaveClient).
 */
public interface SmsGateway {

    NotificationSmsEntity envoyerSms(
            UtilisateurEntity destinataire,
            String telephone,
            TypeNotificationSms type,
            String message,
            RendezVousEntity rendezVous
    );
}
