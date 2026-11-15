package com.ecourtier.domain.annonce.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Machine à états centralisée et explicite pour les transitions de statutPublication d'une annonce.
 * Sépare strictement les prérogatives :
 * - Propriétaire / Agent : aucune modification autorisée via PUT /api/v1/annonces/{id}
 * - Administrateur : suspension (DISPONIBLE -> SUSPENDUE) et levée de suspension (SUSPENDUE -> DISPONIBLE)
 * - Workflow transactionnel : RESERVEE, retour DISPONIBLE, LOUEE et VENDUE uniquement.
 */
@Component
@Slf4j
public class AnnonceStatutStateMachine {

    public void assertModifierAnnonceStatutAutorise(
            AnnonceEntity annonce,
            StatutPublication requestedStatut,
            boolean isAdmin
    ) {
        if (requestedStatut == null || requestedStatut == annonce.getStatutPublication()) {
            return;
        }
        if (!isAdmin) {
            log.warn("[P0-2 SECURITY] Tentative non autorisée de changement de statutPublication ({} -> {}) sur l'annonce {} par un non-administrateur.",
                    annonce.getStatutPublication(), requestedStatut, annonce.getId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Modification du statut de publication non autorisée : seul un administrateur ou le processus transactionnel peut modifier le statut d'une annonce."
            );
        }
    }

    public boolean appliquerTransitionAdmin(
            AnnonceEntity annonce,
            StatutPublication target,
            boolean hasActiveBloqueTransaction
    ) {
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut de publication cible est obligatoire.");
        }

        StatutPublication current = annonce.getStatutPublication();
        if (current == target) {
            return false;
        }

        if (current == StatutPublication.LOUEE || current == StatutPublication.VENDUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transition refusée : impossible de modifier administrativement le statut d'une annonce déjà " + current + "."
            );
        }

        if (current == StatutPublication.RESERVEE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transition refusée : cette annonce est actuellement RESERVEE par un séquestre actif. Procédez d'abord au remboursement ou à la clôture de la transaction."
            );
        }

        if (target == StatutPublication.RESERVEE || target == StatutPublication.LOUEE || target == StatutPublication.VENDUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transition refusée : le statut " + target + " est géré exclusivement par le processus transactionnel et ne peut pas être fixé manuellement."
            );
        }

        if (hasActiveBloqueTransaction) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transition refusée : un séquestre BLOQUE est actif sur cette annonce. Procédez d'abord au remboursement de la transaction."
            );
        }

        if ((current == StatutPublication.DISPONIBLE && target == StatutPublication.SUSPENDUE)
                || (current == StatutPublication.SUSPENDUE && target == StatutPublication.DISPONIBLE)) {
            annonce.setStatutPublication(target);
            log.info("[ANNONCE STATUT ADMIN] Annonce {} : {} -> {}", annonce.getId(), current, target);
            return true;
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Transition administrative non autorisée : " + current + " -> " + target + "."
        );
    }

    public boolean appliquerTransitionTransaction(AnnonceEntity annonce, StatutPublication target) {
        if (annonce == null || target == null) {
            return false;
        }

        StatutPublication current = annonce.getStatutPublication();
        if (current == target) {
            return false;
        }

        boolean allowed = switch (target) {
            case RESERVEE -> current == StatutPublication.DISPONIBLE;
            case DISPONIBLE -> current == StatutPublication.RESERVEE;
            case LOUEE, VENDUE -> current == StatutPublication.DISPONIBLE || current == StatutPublication.RESERVEE;
            case SUSPENDUE -> false;
        };

        if (!allowed) {
            log.warn("[ANNONCE STATUT TRANSACTION REFUSÉ] Annonce {} : transition invalide {} -> {}",
                    annonce.getId(), current, target);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transition transactionnelle d'annonce non autorisée : " + current + " -> " + target + "."
            );
        }

        annonce.setStatutPublication(target);
        log.info("[ANNONCE STATUT TRANSACTION] Annonce {} : {} -> {}", annonce.getId(), current, target);
        return true;
    }
}
