package com.ecourtier.domain.avis.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.annonce.service.AnnonceStatutStateMachine;
import com.ecourtier.domain.avis.dto.SignalementCreateDTO;
import com.ecourtier.domain.avis.dto.SignalementResponseDTO;
import com.ecourtier.domain.avis.dto.TraiterSignalementDTO;
import com.ecourtier.domain.avis.entity.MotifSignalement;
import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import com.ecourtier.domain.avis.repository.SignalementRepository;
import com.ecourtier.domain.transaction.repository.TransactionRepository;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    @Mock
    private TransactionRepository transactionRepository;

    @Spy
    private AnnonceStatutStateMachine annonceStatutStateMachine = new AnnonceStatutStateMachine();

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
        when(annonceRepository.findByIdAndSupprimeFalseForUpdate(annonceId)).thenReturn(Optional.of(annonce));
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

    @Test
    @DisplayName("Traitement admin avec suspension d'une annonce supprimée -> 409 CONFLICT")
    void testTraiterSignalementSuspensionAnnonceSupprimee409() {
        SignalementEntity sig = SignalementEntity.builder()
                .id(signalementId)
                .annonce(annonce)
                .auteur(user)
                .motif(MotifSignalement.ARNAQUE_SUSPECTEE)
                .statut(StatutSignalement.EN_ATTENTE)
                .build();

        when(signalementRepository.findById(signalementId)).thenReturn(Optional.of(sig));
        when(annonceRepository.findByIdAndSupprimeFalseForUpdate(annonceId)).thenReturn(Optional.empty());

        TraiterSignalementDTO dto = TraiterSignalementDTO.builder()
                .statut(StatutSignalement.TRAITE)
                .suspendreAnnonce(true)
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> signalementService.traiterSignalement(signalementId, dto));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(annonceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Auto-signalement de sa propre annonce -> 400 BAD_REQUEST")
    void testCreerSignalementSelfReport400() {
        annonce.setUtilisateur(user);
        when(utilisateurRepository.findByEmail("reporter@test.sn")).thenReturn(Optional.of(user));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));

        SignalementCreateDTO dto = SignalementCreateDTO.builder()
                .motif(MotifSignalement.FAUSSE_ANNONCE)
                .description("Test auto-signalement")
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> signalementService.creerSignalement(annonceId, dto, "reporter@test.sn"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Double signalement d'une même annonce par le même utilisateur -> 409 CONFLICT")
    void testCreerSignalementDuplicate409() {
        when(utilisateurRepository.findByEmail("reporter@test.sn")).thenReturn(Optional.of(user));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));
        when(signalementRepository.existsByAnnonceIdAndAuteurId(annonceId, user.getId())).thenReturn(true);

        SignalementCreateDTO dto = SignalementCreateDTO.builder()
                .motif(MotifSignalement.ARNAQUE_SUSPECTEE)
                .description("Doublon")
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> signalementService.creerSignalement(annonceId, dto, "reporter@test.sn"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("Rejet avec suspendreAnnonce=true ou réouverture d'un signalement traité refusés")
    void testTraiterSignalementTransitionsInvalides() {
        SignalementEntity sig = SignalementEntity.builder()
                .id(signalementId)
                .annonce(annonce)
                .auteur(user)
                .motif(MotifSignalement.ARNAQUE_SUSPECTEE)
                .statut(StatutSignalement.EN_ATTENTE)
                .build();
        when(signalementRepository.findById(signalementId)).thenReturn(Optional.of(sig));

        TraiterSignalementDTO rejectSuspend = TraiterSignalementDTO.builder()
                .statut(StatutSignalement.REJETE)
                .suspendreAnnonce(true)
                .build();
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> signalementService.traiterSignalement(signalementId, rejectSuspend));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        sig.setStatut(StatutSignalement.TRAITE);
        TraiterSignalementDTO reprocess = TraiterSignalementDTO.builder()
                .statut(StatutSignalement.REJETE)
                .suspendreAnnonce(false)
                .build();
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> signalementService.traiterSignalement(signalementId, reprocess));
        assertEquals(HttpStatus.CONFLICT, ex2.getStatusCode());
    }
}
