package com.ecourtier.domain.avis.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.avis.dto.SignalementCreateDTO;
import com.ecourtier.domain.avis.dto.SignalementResponseDTO;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SignalementServiceTest {

    @Mock
    private SignalementRepository signalementRepository;

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private SignalementService signalementService;

    private UtilisateurEntity user;
    private AnnonceEntity annonce;
    private UUID annonceId;
    private UUID signalementId;

    @BeforeEach
    void setUp() {
        annonceId = UUID.randomUUID();
        signalementId = UUID.randomUUID();

        user = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .email("reporter@test.sn")
                .build();

        annonce = AnnonceEntity.builder()
                .id(annonceId)
                .titre("Annonce Suspecte")
                .statutPublication(StatutPublication.DISPONIBLE)
                .build();
    }

    @Test
    @DisplayName("Création d'un signalement d'annonce par un utilisateur")
    void testCreerSignalementSuccess() {
        when(utilisateurRepository.findByEmail("reporter@test.sn")).thenReturn(Optional.of(user));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));

        SignalementEntity savedSig = SignalementEntity.builder()
                .id(signalementId)
                .annonce(annonce)
                .auteur(user)
                .motif(MotifSignalement.FAUSSE_ANNONCE)
                .description("Suspicion de faux visuels")
                .statut(StatutSignalement.EN_ATTENTE)
                .build();

        when(signalementRepository.save(any(SignalementEntity.class))).thenReturn(savedSig);

        SignalementCreateDTO dto = SignalementCreateDTO.builder()
                .motif(MotifSignalement.FAUSSE_ANNONCE)
                .description("Suspicion de faux visuels")
                .build();

        SignalementResponseDTO res = signalementService.creerSignalement(annonceId, dto, "reporter@test.sn");

        assertNotNull(res);
        assertEquals(MotifSignalement.FAUSSE_ANNONCE, res.getMotif());
        assertEquals(StatutSignalement.EN_ATTENTE, res.getStatut());
    }

    @Test
    @DisplayName("Traitement admin d'un signalement avec option de suspension d'annonce")
    void testTraiterSignalementWithSuspension() {
        SignalementEntity sig = SignalementEntity.builder()
                .id(signalementId)
                .annonce(annonce)
                .auteur(user)
                .motif(MotifSignalement.ARNAQUE_SUSPECTEE)
                .statut(StatutSignalement.EN_ATTENTE)
                .build();

        when(signalementRepository.findById(signalementId)).thenReturn(Optional.of(sig));
        when(signalementRepository.save(any(SignalementEntity.class))).thenReturn(sig);

        TraiterSignalementDTO dto = TraiterSignalementDTO.builder()
                .statut(StatutSignalement.TRAITE)
                .suspendreAnnonce(true)
                .build();

        SignalementResponseDTO res = signalementService.traiterSignalement(signalementId, dto);

        assertEquals(StatutSignalement.TRAITE, res.getStatut());
        assertEquals(StatutPublication.SUSPENDUE, annonce.getStatutPublication(), "L'annonce doit passer à SUSPENDUE");
        verify(annonceRepository).save(annonce);
    }
}
