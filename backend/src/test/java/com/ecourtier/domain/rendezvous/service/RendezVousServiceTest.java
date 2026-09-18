package com.ecourtier.domain.rendezvous.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.rendezvous.mapper.RendezVousMapper;
import com.ecourtier.domain.rendezvous.repository.RendezVousRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RendezVousServiceTest {

    @Mock
    private RendezVousRepository rendezVousRepository;

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private RendezVousMapper rendezVousMapper;

    @InjectMocks
    private RendezVousService rendezVousService;

    private UUID annonceId;
    private UUID rdvId;
    private LocalDateTime rdvTime;
    private RendezVousEntity rdvEntity;

    @BeforeEach
    void setUp() {
        annonceId = UUID.randomUUID();
        rdvId = UUID.randomUUID();
        rdvTime = LocalDateTime.of(2026, 10, 15, 14, 0);

        AnnonceEntity annonce = AnnonceEntity.builder().id(annonceId).titre("Villa Almadies").build();
        UtilisateurEntity demandeur = UtilisateurEntity.builder().id(UUID.randomUUID()).email("locataire@test.sn").build();

        rdvEntity = RendezVousEntity.builder()
                .id(rdvId)
                .annonce(annonce)
                .demandeur(demandeur)
                .dateHeure(rdvTime)
                .statut(StatutRendezVous.DEMANDE)
                .build();
    }

    @Test
    @DisplayName("Test : Double réservation sur le même créneau (± 1h) déjà CONFIRMÉ rejetée avec 409 Conflict")
    void testConfirmerRendezVousDoubleBookingRejete() {
        when(rendezVousRepository.findById(rdvId)).thenReturn(Optional.of(rdvEntity));

        // Simuler qu'un autre RDV existe déjà à 14h30 (dans la plage ± 1h)
        RendezVousEntity conflict = RendezVousEntity.builder()
                .id(UUID.randomUUID())
                .dateHeure(rdvTime.plusMinutes(30))
                .statut(StatutRendezVous.CONFIRME)
                .build();

        when(rendezVousRepository.findConflictingConfirmedSlot(
                eq(annonceId),
                eq(rdvId),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(conflict));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                rendezVousService.confirmerRendezVous(rdvId, "owner@test.sn")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("déjà confirmé sur ce créneau"));
    }

    @Test
    @DisplayName("Test : Confirmation réussie quand le créneau horaire est totalement libre")
    void testConfirmerRendezVousSucces() {
        when(rendezVousRepository.findById(rdvId)).thenReturn(Optional.of(rdvEntity));
        when(rendezVousRepository.findConflictingConfirmedSlot(
                eq(annonceId),
                eq(rdvId),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(Collections.emptyList());

        rdvEntity.setStatut(StatutRendezVous.CONFIRME);
        when(rendezVousRepository.save(any(RendezVousEntity.class))).thenReturn(rdvEntity);
        when(rendezVousMapper.toResponseDTO(rdvEntity)).thenReturn(
                RendezVousResponseDTO.builder().id(rdvId).statut(StatutRendezVous.CONFIRME).build()
        );

        RendezVousResponseDTO result = rendezVousService.confirmerRendezVous(rdvId, "owner@test.sn");
        assertNotNull(result);
        assertEquals(StatutRendezVous.CONFIRME, result.getStatut());
    }

    private void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
