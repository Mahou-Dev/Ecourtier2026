package com.ecourtier.domain.annonce.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnonceStatutStateMachineTest {

    private final AnnonceStatutStateMachine stateMachine = new AnnonceStatutStateMachine();

    private AnnonceEntity annonceWithStatus(StatutPublication statut) {
        return AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Appartement Test")
                .statutPublication(statut)
                .build();
    }

    @Test
    @DisplayName("P0-2 : Propriétaire / Agent ne peut modifier aucun statut via PUT /annonces/{id} (403)")
    void testNonAdminCannotChangeAnyStatut() {
        for (StatutPublication initial : StatutPublication.values()) {
            for (StatutPublication target : StatutPublication.values()) {
                AnnonceEntity annonce = annonceWithStatus(initial);
                if (initial == target) {
                    stateMachine.assertModifierAnnonceStatutAutorise(annonce, target, false);
                } else {
                    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                            () -> stateMachine.assertModifierAnnonceStatutAutorise(annonce, target, false));
                    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
                }
            }
            // Valeur null (non fournie dans le PUT) : autorisée et préserve l'état existant
            stateMachine.assertModifierAnnonceStatutAutorise(annonceWithStatus(initial), null, false);
        }
    }

    @Test
    @DisplayName("P0-2 : Admin autorisé uniquement sur DISPONIBLE <-> SUSPENDUE sans séquestre BLOQUE")
    void testAdminAllowedAndForbiddenTransitions() {
        AnnonceEntity disponible = annonceWithStatus(StatutPublication.DISPONIBLE);
        assertTrue(stateMachine.appliquerTransitionAdmin(disponible, StatutPublication.SUSPENDUE, false));
        assertEquals(StatutPublication.SUSPENDUE, disponible.getStatutPublication());

        // Idempotent si déjà au même statut
        assertFalse(stateMachine.appliquerTransitionAdmin(disponible, StatutPublication.SUSPENDUE, false));

        assertTrue(stateMachine.appliquerTransitionAdmin(disponible, StatutPublication.DISPONIBLE, false));
        assertEquals(StatutPublication.DISPONIBLE, disponible.getStatutPublication());

        // Suspension refusée si séquestre BLOQUE actif
        ResponseStatusException exBloque = assertThrows(ResponseStatusException.class,
                () -> stateMachine.appliquerTransitionAdmin(annonceWithStatus(StatutPublication.DISPONIBLE), StatutPublication.SUSPENDUE, true));
        assertEquals(HttpStatus.CONFLICT, exBloque.getStatusCode());

        // Cibles réservées au workflow transactionnel interdites en transition admin
        for (StatutPublication forbiddenTarget : new StatutPublication[]{
                StatutPublication.RESERVEE, StatutPublication.LOUEE, StatutPublication.VENDUE
        }) {
            ResponseStatusException exTarget = assertThrows(ResponseStatusException.class,
                    () -> stateMachine.appliquerTransitionAdmin(annonceWithStatus(StatutPublication.DISPONIBLE), forbiddenTarget, false));
            assertEquals(HttpStatus.CONFLICT, exTarget.getStatusCode());
        }

        // États terminaux / réservés immuables par l'admin
        for (StatutPublication lockedSource : new StatutPublication[]{
                StatutPublication.RESERVEE, StatutPublication.LOUEE, StatutPublication.VENDUE
        }) {
            ResponseStatusException exSource = assertThrows(ResponseStatusException.class,
                    () -> stateMachine.appliquerTransitionAdmin(annonceWithStatus(lockedSource), StatutPublication.DISPONIBLE, false));
            assertEquals(HttpStatus.CONFLICT, exSource.getStatusCode());
        }
    }

    @Test
    @DisplayName("P0-2 : Machine à états transactionnelle autorise DISPONIBLE<->RESERVEE et -> LOUEE/VENDUE, interdit SUSPENDUE")
    void testTransactionTransitions() {
        AnnonceEntity a1 = annonceWithStatus(StatutPublication.DISPONIBLE);
        assertTrue(stateMachine.appliquerTransitionTransaction(a1, StatutPublication.RESERVEE));
        assertEquals(StatutPublication.RESERVEE, a1.getStatutPublication());
        assertTrue(stateMachine.appliquerTransitionTransaction(a1, StatutPublication.DISPONIBLE));
        assertEquals(StatutPublication.DISPONIBLE, a1.getStatutPublication());

        AnnonceEntity a2 = annonceWithStatus(StatutPublication.RESERVEE);
        assertTrue(stateMachine.appliquerTransitionTransaction(a2, StatutPublication.LOUEE));
        assertEquals(StatutPublication.LOUEE, a2.getStatutPublication());

        AnnonceEntity a3 = annonceWithStatus(StatutPublication.DISPONIBLE);
        assertTrue(stateMachine.appliquerTransitionTransaction(a3, StatutPublication.VENDUE));
        assertEquals(StatutPublication.VENDUE, a3.getStatutPublication());

        // Transition vers SUSPENDUE interdite au workflow transactionnel
        ResponseStatusException exSusp = assertThrows(ResponseStatusException.class,
                () -> stateMachine.appliquerTransitionTransaction(annonceWithStatus(StatutPublication.DISPONIBLE), StatutPublication.SUSPENDUE));
        assertEquals(HttpStatus.CONFLICT, exSusp.getStatusCode());

        // Annonce SUSPENDUE ne peut pas passer à RESERVEE / LOUEE / DISPONIBLE par transaction
        for (StatutPublication t : new StatutPublication[]{
                StatutPublication.RESERVEE, StatutPublication.DISPONIBLE, StatutPublication.LOUEE, StatutPublication.VENDUE
        }) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> stateMachine.appliquerTransitionTransaction(annonceWithStatus(StatutPublication.SUSPENDUE), t));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }
}
