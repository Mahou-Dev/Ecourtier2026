package com.ecourtier.domain.annonce.service;

import com.ecourtier.domain.annonce.dto.AnnonceResponseDTO;
import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.ModeTransaction;
import com.ecourtier.domain.annonce.entity.StatutPublication;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import com.ecourtier.domain.annonce.entity.TypeBien;
import com.ecourtier.domain.annonce.repository.AnnonceRepository;
import com.ecourtier.domain.utilisateur.entity.Role;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import com.ecourtier.domain.utilisateur.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MesAnnoncesFilterTest {

    @Autowired
    private AnnonceService annonceService;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private UtilisateurEntity owner;
    private AnnonceEntity loueeAnnonce;
    private AnnonceEntity vendueAnnonce;
    private AnnonceEntity suspendueAnnonce;

    @BeforeEach
    void setUp() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        owner = utilisateurRepository.save(UtilisateurEntity.builder()
                .prenom("Babacar")
                .nom("Diouf")
                .email("babacar.mesannonces@test.sn")
                .motDePasse("Pass1234!")
                .telephone("+221770003333")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        loueeAnnonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Annonce Louée")
                .description("Logement déjà loué")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(400000))
                .superficie(110.0)
                .nombrePieces(4)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.LOUEE)
                .utilisateur(owner)
                .build());

        vendueAnnonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Annonce Vendue")
                .description("Bien immobilier vendu")
                .type(TypeBien.VILLA)
                .modeTransaction(ModeTransaction.VENTE)
                .prix(BigDecimal.valueOf(60000000))
                .superficie(300.0)
                .nombrePieces(5)
                .quartier("Almadies")
                .ville("Dakar")
                .latitude(14.74)
                .longitude(-17.51)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.VENDUE)
                .utilisateur(owner)
                .build());

        suspendueAnnonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Annonce Suspendue")
                .description("Annonce temporairement suspendue")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(150000))
                .superficie(35.0)
                .nombrePieces(1)
                .quartier("Fann")
                .ville("Dakar")
                .latitude(14.69)
                .longitude(-17.46)
                .statutVerification(StatutVerification.EN_ATTENTE)
                .statutPublication(StatutPublication.SUSPENDUE)
                .utilisateur(owner)
                .build());
    }

    @Test
    @DisplayName("Le filtre 'mes_annonces' retourne TOUTES les annonces du propriétaire y compris LOUEE, VENDUE et SUSPENDUE")
    void testMesAnnoncesIncludesUnavailableStatusListings() {
        // Recherche sans filtre auteur -> 0 annonces publiques car LOUEE, VENDUE, SUSPENDUE sont exclues par défaut
        Page<AnnonceResponseDTO> publicPage = annonceService.rechercherAnnonces(
                PageRequest.of(0, 10), null, null, null, null, null, null, null, null, null, null, null, null, false, null
        );
        assertEquals(0, publicPage.getTotalElements(), "La recherche publique ne doit retourner aucune des annonces LOUEE/VENDUE/SUSPENDUE");

        // Recherche avec auteurEmail -> Retourne 3 annonces du propriétaire
        Page<AnnonceResponseDTO> mesAnnoncesPage = annonceService.rechercherAnnonces(
                PageRequest.of(0, 10), null, null, null, null, null, null, null, null, null, null, null, null, false, "babacar.mesannonces@test.sn"
        );

        assertEquals(3, mesAnnoncesPage.getTotalElements(), "Le filtre 'mes annonces' doit retourner les 3 annonces du propriétaire");
        assertTrue(mesAnnoncesPage.getContent().stream().anyMatch(a -> a.getId().equals(loueeAnnonce.getId())));
        assertTrue(mesAnnoncesPage.getContent().stream().anyMatch(a -> a.getId().equals(vendueAnnonce.getId())));
        assertTrue(mesAnnoncesPage.getContent().stream().anyMatch(a -> a.getId().equals(suspendueAnnonce.getId())));
    }
}
