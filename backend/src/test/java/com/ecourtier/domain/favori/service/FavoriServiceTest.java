package com.ecourtier.domain.favori.service;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.mapper.AnnonceMapper;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.favori.entity.FavoriEntity;
import com.ecourtier.domain.favori.repository.FavoriRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FavoriServiceTest {

    @Mock
    private FavoriRepository favoriRepository;

    @Mock
    private AnnonceRepository annonceRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private AnnonceMapper annonceMapper;

    @InjectMocks
    private FavoriService favoriService;

    private UtilisateurEntity locataire;
    private UtilisateurEntity autreUtilisateur;
    private AnnonceEntity annonce;
    private UUID annonceId;
    private UUID locataireId;
    private UUID autreId;

    @BeforeEach
    void setUp() {
        annonceId = UUID.randomUUID();
        locataireId = UUID.randomUUID();
        autreId = UUID.randomUUID();

        locataire = UtilisateurEntity.builder()
                .id(locataireId)
                .email("locataire@test.sn")
                .prenom("Moussa")
                .nom("Diop")
                .role(Role.LOCATAIRE)
                .build();

        autreUtilisateur = UtilisateurEntity.builder()
                .id(autreId)
                .email("autre@test.sn")
                .prenom("Awa")
                .nom("Ndiaye")
                .role(Role.LOCATAIRE)
                .build();

        annonce = AnnonceEntity.builder()
                .id(annonceId)
                .titre("Annonce Test Favori")
                .build();
    }

    @Test
    @DisplayName("Ajout en double du même favori → 409 CONFLICT")
    void testAjouterFavoriDoublon409() {
        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));
        when(favoriRepository.existsByUtilisateurIdAndAnnonceId(locataireId, annonceId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> favoriService.ajouterFavori(annonceId, "locataire@test.sn"));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("déjà dans vos favoris"));
        verify(favoriRepository, never()).save(any());
    }

    @Test
    @DisplayName("Suppression d'un favori inexistant → 404 NOT_FOUND")
    void testSupprimerFavoriInexistant404() {
        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(favoriRepository.findByUtilisateurIdAndAnnonceId(locataireId, annonceId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> favoriService.supprimerFavori(annonceId, "locataire@test.sn"));

        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("n'existe pas"));
    }

    @Test
    @DisplayName("Ajout d'un favori réussi → save() appelé une fois")
    void testAjouterFavoriSucces() {
        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(annonceRepository.findByIdAndSupprimeFalse(annonceId)).thenReturn(Optional.of(annonce));
        when(favoriRepository.existsByUtilisateurIdAndAnnonceId(locataireId, annonceId)).thenReturn(false);

        favoriService.ajouterFavori(annonceId, "locataire@test.sn");

        verify(favoriRepository).save(any(FavoriEntity.class));
    }

    @Test
    @DisplayName("getAnnonceIdsFavorites retourne un Set vide pour un email null (anonyme)")
    void testGetFavorisAnonymeFalse() {
        var result = favoriService.getAnnonceIdsFavorites(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Suppression d'un favori réussie → delete() appelé")
    void testSupprimerFavoriSucces() {
        FavoriEntity favori = FavoriEntity.builder()
                .id(UUID.randomUUID())
                .utilisateur(locataire)
                .annonce(annonce)
                .build();

        when(utilisateurRepository.findByEmail("locataire@test.sn")).thenReturn(Optional.of(locataire));
        when(favoriRepository.findByUtilisateurIdAndAnnonceId(locataireId, annonceId)).thenReturn(Optional.of(favori));

        favoriService.supprimerFavori(annonceId, "locataire@test.sn");

        verify(favoriRepository).delete(favori);
    }
}
