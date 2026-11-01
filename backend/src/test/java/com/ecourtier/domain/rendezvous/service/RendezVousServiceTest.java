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
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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

    @Mock
    private SmsGateway smsGateway;

    @InjectMocks
    private RendezVousService rendezVousService;

    private UtilisateurEntity locataire;
    private UtilisateurEntity proprietaire;
    private AnnonceEntity annonce;
    private RendezVousEntity rdv;

    @BeforeEach
    void setUp() {
        locataire = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("locataire.rdv@test.sn")
                .nom("Diop")
                .prenom("Moussa")
                .telephone("+221771112233")
                .role(Role.LOCATAIRE)
                .build();

        proprietaire = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("proprio.rdv@test.sn")
                .nom("Sow")
                .prenom("Ousmane")
                .telephone("+221772223344")
                .role(Role.PROPRIETAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Appartement Dakar Fann")
                .statutPublication(StatutPublication.DISPONIBLE)
                .confirmationAutomatique(true)
                .utilisateur(proprietaire)
                .build();

        rdv = RendezVousEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .demandeur(locataire)
                .dateHeure(LocalDateTime.now().plusDays(1))
                .statut(StatutRendezVous.DEMANDE)
                .rappelEnvoye(false)
                .build();
    }

    @Test
    @DisplayName("Demande de RDV avec confirmationAutomatique=true sans conflit passe directement à CONFIRME")
    void testDemanderRendezVousConfirmationAutomatique() {
        RendezVousRequestDTO req = new RendezVousRequestDTO();
        req.setDateHeure(LocalDateTime.now().plusDays(2));

        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.findConfirmedInWindow(eq(annonce.getId()), any(), any())).thenReturn(List.of());
        when(rendezVousRepository.save(any(RendezVousEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rendezVousMapper.toResponseDTO(any())).thenReturn(new RendezVousResponseDTO());

        RendezVousResponseDTO res = rendezVousService.demanderRendezVous(annonce.getId(), req, "locataire.rdv@test.sn");
        assertNotNull(res);

        ArgumentCaptor<RendezVousEntity> captor = ArgumentCaptor.forClass(RendezVousEntity.class);
        verify(rendezVousRepository).save(captor.capture());
        assertEquals(StatutRendezVous.CONFIRME, captor.getValue().getStatut());
    }

    @Test
    @DisplayName("Demande de RDV avec conflit à ±60 min reste DEMANDE même si confirmationAutomatique=true")
    void testDemanderRendezVousConflitResteDemande() {
        RendezVousRequestDTO req = new RendezVousRequestDTO();
        req.setDateHeure(LocalDateTime.now().plusDays(2));

        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.findConfirmedInWindow(eq(annonce.getId()), any(), any()))
                .thenReturn(List.of(new RendezVousEntity()));
        when(rendezVousRepository.save(any(RendezVousEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rendezVousMapper.toResponseDTO(any())).thenReturn(new RendezVousResponseDTO());

        rendezVousService.demanderRendezVous(annonce.getId(), req, "locataire.rdv@test.sn");

        ArgumentCaptor<RendezVousEntity> captor = ArgumentCaptor.forClass(RendezVousEntity.class);
        verify(rendezVousRepository).save(captor.capture());
        assertEquals(StatutRendezVous.DEMANDE, captor.getValue().getStatut());
    }

    @Test
    @DisplayName("Échec du SmsGateway n'affecte jamais la création, confirmation ou annulation du RDV")
    void testEchecSmsGatewaySansEffetSurRdv() {
        RendezVousRequestDTO req = new RendezVousRequestDTO();
        req.setDateHeure(LocalDateTime.now().plusDays(2));

        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonce.getId())).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.findConfirmedInWindow(eq(annonce.getId()), any(), any())).thenReturn(List.of());
        when(rendezVousRepository.save(any(RendezVousEntity.class))).thenReturn(rdv);
        when(rendezVousRepository.findById(rdv.getId())).thenReturn(Optional.of(rdv));
        when(rendezVousRepository.findConflictingConfirmedSlot(eq(annonce.getId()), eq(rdv.getId()), any(), any()))
                .thenReturn(List.of());
        when(rendezVousMapper.toResponseDTO(any())).thenReturn(new RendezVousResponseDTO());
        when(smsGateway.envoyerSms(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Panne simulée du fournisseur SMS"));

        assertDoesNotThrow(() -> rendezVousService.demanderRendezVous(annonce.getId(), req, "locataire.rdv@test.sn"));
        assertDoesNotThrow(() -> rendezVousService.confirmerRendezVous(rdv.getId(), "proprio.rdv@test.sn"));
        assertDoesNotThrow(() -> rendezVousService.annulerRendezVous(rdv.getId(), new RendezVousAnnulationDTO("Imprévu"), "locataire.rdv@test.sn"));

        verify(smsGateway, atLeastOnce()).envoyerSms(any(), any(), any(TypeNotificationSms.class), any(), any());
    }

    @Test
    @DisplayName("Demande de RDV refusée si bien indisponible")
    void testDemanderRendezVousUnavailable() {
        annonce.setStatutPublication(StatutPublication.LOUEE);
        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(locataire));
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
                () -> rendezVousService.confirmerRendezVous(rdv.getId(), "proprio.rdv@test.sn"));
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

        RendezVousResponseDTO res = rendezVousService.confirmerRendezVous(rdv.getId(), "proprio.rdv@test.sn");
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
        rendezVousService.annulerRendezVous(rdv.getId(), cancelDto, "locataire.rdv@test.sn");
        assertEquals(StatutRendezVous.ANNULE, rdv.getStatut());

        rendezVousService.marquerEffectue(rdv.getId(), "proprio.rdv@test.sn");
        assertEquals(StatutRendezVous.EFFECTUE, rdv.getStatut());
    }

    @Test
    @DisplayName("Obtenir mes RDV et RDV Annonce")
    void testObtenirRDVs() {
        when(utilisateurRepository.findByEmail("locataire.rdv@test.sn")).thenReturn(Optional.of(locataire));
        when(rendezVousRepository.findByDemandeurIdOrAnnonceUtilisateurIdOrderByDateHeureDesc(locataire.getId(), locataire.getId()))
                .thenReturn(List.of(rdv));
        when(rendezVousMapper.toResponseDTO(rdv)).thenReturn(new RendezVousResponseDTO());

        List<RendezVousResponseDTO> list1 = rendezVousService.obtenirMesRendezVous("locataire.rdv@test.sn");
        assertEquals(1, list1.size());

        when(rendezVousRepository.findByAnnonceIdOrderByDateHeureAsc(annonce.getId())).thenReturn(List.of(rdv));
        List<RendezVousResponseDTO> list2 = rendezVousService.obtenirRendezVousAnnonce(annonce.getId(), "proprio.rdv@test.sn");
        assertEquals(1, list2.size());
    }

    @Test
    @DisplayName("Simulation des rappels SMS automatique basée sur dateHeure de demain (horloge injectée)")
    void testSimulerRappelsDemainAvecHorlogeInjectee() {
        java.time.Clock fixedClock = java.time.Clock.fixed(
                java.time.Instant.parse("2026-11-01T10:00:00Z"),
                java.time.ZoneId.of("Africa/Dakar")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(rendezVousService, "clock", fixedClock);

        org.mockito.ArgumentCaptor<LocalDateTime> startCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> endCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);

        when(rendezVousRepository.findByStatutAndRappelEnvoyeFalseAndDateHeureBetween(
                eq(StatutRendezVous.CONFIRME),
                startCaptor.capture(),
                endCaptor.capture()
        )).thenReturn(List.of(rdv));

        int count = rendezVousService.simulerRappelsDemain();
        assertEquals(1, count);
        assertTrue(rdv.getRappelEnvoye());

        // Prouve que la requête porte strictement sur dateHeure = demain (2026-11-02), indépendamment de dateCreation
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0, 0), startCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 2, 23, 59, 59), endCaptor.getValue());

        verify(rendezVousRepository).save(rdv);
        verify(smsGateway).envoyerSms(eq(locataire), eq(locataire.getTelephone()), eq(TypeNotificationSms.RDV_RAPPEL), any(), eq(rdv));
    }
}
