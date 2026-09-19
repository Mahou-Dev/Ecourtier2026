package com.ecourtier.domain.avis.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.dto.AvisCreateDTO;
import com.ecourtier.domain.avis.dto.AvisResponseDTO;
import com.ecourtier.domain.avis.dto.AvisSummaryDTO;
import com.ecourtier.domain.avis.dto.ReputationResponseDTO;
import com.ecourtier.domain.avis.entity.AvisEntity;
import com.ecourtier.domain.avis.repository.AvisRepository;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AvisServiceTest {

    @Mock
    private AvisRepository avisRepository;

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private RendezVousRepository rendezVousRepository;

    @InjectMocks
    private AvisService avisService;

    private UtilisateurEntity locataire;
    private AnnonceEntity annonce;
    private UUID annonceId;
    private UUID locataireId;

    @BeforeEach
    void setUp() {
        annonceId = UUID.randomUUID();
        locataireId = UUID.randomUUID();

        locataire = UtilisateurEntity.builder()
                .id(locataireId)
                .email("locataire@test.sn")
                .prenom("Moussa")
                .nom("Diop")
                .role(Role.LOCATAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(annonceId)
                .titre("Annonce Test Avis")
                .build();
    }

    @Test
    @DisplayName("Refus 403 si un locataire n'a pas eu de RDV EFFECTUE sur l'annonce")
    void testCreerAvisWithoutCompletedRdvThrowsForbidden() {
        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.existsByAnnonceIdAndDemandeurIdAndStatut(annonceId, locataireId, StatutRendezVous.EFFECTUE))
                .thenReturn(false);

        AvisCreateDTO dto = AvisCreateDTO.builder().note(5).commentaire("Tres bien").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            avisService.creerAvis(annonceId, dto, "locataire@test.sn");
        });

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Refus 409 si le locataire a déjà déposé un avis sur la même annonce")
    void testCreerAvisDuplicateThrowsConflict() {
        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.existsByAnnonceIdAndDemandeurIdAndStatut(annonceId, locataireId, StatutRendezVous.EFFECTUE))
                .thenReturn(true);
        when(avisRepository.existsByAnnonceIdAndAuteurId(annonceId, locataireId)).thenReturn(true);

        AvisCreateDTO dto = AvisCreateDTO.builder().note(4).commentaire("Bis").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            avisService.creerAvis(annonceId, dto, "locataire@test.sn");
        });

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Création réussie de l'avis lorsque la visite est EFFECTUE et aucun avis antérieur n'existe")
    void testCreerAvisSuccess() {
        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));
        when(rendezVousRepository.existsByAnnonceIdAndDemandeurIdAndStatut(annonceId, locataireId, StatutRendezVous.EFFECTUE))
                .thenReturn(true);
        when(avisRepository.existsByAnnonceIdAndAuteurId(annonceId, locataireId)).thenReturn(false);

        AvisEntity savedEntity = AvisEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .auteur(locataire)
                .note(5)
                .commentaire("Parfait")
                .build();

        when(avisRepository.save(any(AvisEntity.class))).thenReturn(savedEntity);

        AvisCreateDTO dto = AvisCreateDTO.builder().note(5).commentaire("Parfait").build();
        AvisResponseDTO res = avisService.creerAvis(annonceId, dto, "locataire@test.sn");

        assertNotNull(res);
        assertEquals(5, res.getNote());
        assertEquals("Moussa", res.getAuteurPrenom());
        verify(avisRepository).save(any(AvisEntity.class));
    }

    @Test
    @DisplayName("Calcul correct du résumé des avis et de la réputation du propriétaire")
    void testObtenirAvisSummaryAndReputation() {
        when(annonceRepository.existsById(annonceId)).thenReturn(true);
        when(avisRepository.findByAnnonceIdOrderByDateCreationDesc(annonceId)).thenReturn(List.of());
        when(avisRepository.findAverageNoteByAnnonceId(annonceId)).thenReturn(4.5);
        when(avisRepository.countByAnnonceId(annonceId)).thenReturn(10L);

        AvisSummaryDTO summary = avisService.obtenirAvisAnnonce(annonceId);

        assertEquals(4.5, summary.getNoteMoyenne());
        assertEquals(10L, summary.getNombreAvis());

        UUID ownerId = UUID.randomUUID();
        when(utilisateurRepository.existsById(ownerId)).thenReturn(true);
        when(avisRepository.findAverageNoteByUtilisateurId(ownerId)).thenReturn(4.8);
        when(avisRepository.countByUtilisateurId(ownerId)).thenReturn(25L);

        ReputationResponseDTO rep = avisService.obtenirReputationUtilisateur(ownerId);
        assertEquals(4.8, rep.getNoteMoyenne());
        assertEquals(25L, rep.getNombreAvis());
    }
}
