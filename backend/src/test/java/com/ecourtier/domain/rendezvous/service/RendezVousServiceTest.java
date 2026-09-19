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
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RendezVousServiceTest {

    @Mock
    private RendezVousRepository rendezVousRepository;

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private RendezVousMapper rendezVousMapper;

    @InjectMocks
    private RendezVousService rendezVousService;

    private UtilisateurEntity user;
    private AnnonceEntity annonce;
    private RendezVousEntity rdv;

    @BeforeEach
    void setUp() {
        user = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("locataire.rdv@test.sn")
                .nom("Diop")
                .prenom("Moussa")
                .role(Role.LOCATAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Appartement Dakar Fann")
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(user)
                .build();

        rdv = RendezVousEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .demandeur(user)
                .dateHeure(LocalDateTime.now().plusDays(1))
                .statut(StatutRendezVous.DEMANDE)
                .rappelEnvoye(false)
                .build();
    }

    @Test
    @DisplayName("Demande de RDV avec succès")
    void testDemanderRendezVousSuccess() {
        RendezVousRequestDTO req = new RendezVousRequestDTO();
        req.setDateHeure(LocalDateTime.now().plusDays(2));

        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(user));
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.save(any(RendezVousEntity.class))).thenReturn(rdv);
        when(rendezVousMapper.toResponseDTO(any())).thenReturn(new RendezVousResponseDTO());

        RendezVousResponseDTO res = rendezVousService.demanderRendezVous(annonce.getId(), req, "locataire.rdv@test.sn");
        assertNotNull(res);
    }

    @Test
    @DisplayName("Demande de RDV refusée si bien indisponible")
    void testDemanderRendezVousUnavailable() {
        annonce.setStatutPublication(StatutPublication.LOUEE);
        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(user));
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));

        RendezVousRequestDTO req = new RendezVousRequestDTO();
        req.setDateHeure(LocalDateTime.now().plusDays(2));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> rendezVousService.demanderRendezVous(annonce.getId(), req, "locataire.rdv@test.sn"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Confirmation de RDV avec conflit de créneau (double booking)")
    void testConfirmerRendezVousConflict() {
        when(rendezVousRepository.findById(rdv.getId())).thenReturn(Optional.of(rdv));
        when(rendezVousRepository.findConflictingConfirmedSlot(eq(annonce.getId()), eq(rdv.getId()), any(), any()))
                .thenReturn(List.of(new RendezVousEntity()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> rendezVousService.confirmerRendezVous(rdv.getId(), "locataire.rdv@test.sn"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Confirmation de RDV réussie")
    void testConfirmerRendezVousSuccess() {
        when(rendezVousRepository.findById(rdv.getId())).thenReturn(Optional.of(rdv));
        when(rendezVousRepository.findConflictingConfirmedSlot(eq(annonce.getId()), eq(rdv.getId()), any(), any()))
                .thenReturn(List.of());
        when(rendezVousRepository.save(any())).thenReturn(rdv);
        when(rendezVousMapper.toResponseDTO(any())).thenReturn(new RendezVousResponseDTO());

        RendezVousResponseDTO res = rendezVousService.confirmerRendezVous(rdv.getId(), "locataire.rdv@test.sn");
        assertNotNull(res);
        assertEquals(StatutRendezVous.CONFIRME, rdv.getStatut());
    }

    @Test
    @DisplayName("Annulation et Marquer Effectué RDV")
    void testAnnulerEtEffectue() {
        when(rendezVousRepository.findById(rdv.getId())).thenReturn(Optional.of(rdv));
        when(rendezVousRepository.save(any())).thenReturn(rdv);
        when(rendezVousMapper.toResponseDTO(any())).thenReturn(new RendezVousResponseDTO());

        RendezVousAnnulationDTO cancelDto = new RendezVousAnnulationDTO();
        cancelDto.setMotif("Empêchement");
        rendezVousService.annulerRendezVous(rdv.getId(), cancelDto, "user@test.sn");
        assertEquals(StatutRendezVous.ANNULE, rdv.getStatut());

        rendezVousService.marquerEffectue(rdv.getId(), "user@test.sn");
        assertEquals(StatutRendezVous.EFFECTUE, rdv.getStatut());
    }

    @Test
    @DisplayName("Obtenir mes RDV et RDV Annonce")
    void testObtenirRDVs() {
        when(utilisateurRepository.findByEmail("user@test.sn")).thenReturn(Optional.of(user));
        when(rendezVousRepository.findByDemandeurIdOrAnnonceUtilisateurIdOrderByDateHeureDesc(user.getId(), user.getId()))
                .thenReturn(List.of(rdv));
        when(rendezVousMapper.toResponseDTO(rdv)).thenReturn(new RendezVousResponseDTO());

        List<RendezVousResponseDTO> list1 = rendezVousService.obtenirMesRendezVous("user@test.sn");
        assertEquals(1, list1.size());

        when(rendezVousRepository.findByAnnonceIdOrderByDateHeureAsc(annonce.getId())).thenReturn(List.of(rdv));
        List<RendezVousResponseDTO> list2 = rendezVousService.obtenirRendezVousAnnonce(annonce.getId(), "user@test.sn");
        assertEquals(1, list2.size());
    }

    @Test
    @DisplayName("Simulation des rappels SMS automatique")
    void testSimulerRappelsDemain() {
        when(rendezVousRepository.findByStatutAndRappelEnvoyeFalseAndDateHeureBetween(eq(StatutRendezVous.CONFIRME), any(), any()))
                .thenReturn(List.of(rdv));

        int count = rendezVousService.simulerRappelsDemain();
        assertEquals(1, count);
        assertTrue(rdv.getRappelEnvoye());
        verify(rendezVousRepository).save(rdv);
    }
}
