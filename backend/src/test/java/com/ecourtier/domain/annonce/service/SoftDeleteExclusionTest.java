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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SoftDeleteExclusionTest {

    @Autowired
    private AnnonceService annonceService;

    @Autowired
    private AnnonceRepository annonceRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AnnonceEntity activeAnnonce;
    private AnnonceEntity deletedAnnonce;

    @BeforeEach
    void setUp() {
        annonceRepository.deleteAll();
        utilisateurRepository.deleteAll();

        UtilisateurEntity owner = utilisateurRepository.save(UtilisateurEntity.builder()
                .prenom("Owner")
                .nom("SoftDelete")
                .email("softdelete.proprio@test.sn")
                .motDePasse("Pass1234!")
                .telephone("+221770002222")
                .role(Role.PROPRIETAIRE)
                .actif(true)
                .build());

        activeAnnonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Annonce Active")
                .description("Superbe annonce active")
                .type(TypeBien.APPARTEMENT)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(300000))
                .superficie(75.0)
                .nombrePieces(3)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .supprime(false)
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(owner)
                .build());

        deletedAnnonce = annonceRepository.save(AnnonceEntity.builder()
                .titre("Annonce Supprimée")
                .description("Annonce supprimée logiquement")
                .type(TypeBien.STUDIO)
                .modeTransaction(ModeTransaction.LOCATION)
                .prix(BigDecimal.valueOf(120000))
                .superficie(30.0)
                .nombrePieces(1)
                .quartier("Mermoz")
                .ville("Dakar")
                .latitude(14.70)
                .longitude(-17.47)
                .supprime(true) // Soft deleted
                .statutVerification(StatutVerification.VERIFIEE)
                .statutPublication(StatutPublication.DISPONIBLE)
                .utilisateur(owner)
                .build());
    }

    @Test
    @DisplayName("Exclusion stricte des annonces supprime=true des résultats de recherche publique")
    void testSoftDeletedListingsExcludedFromSearch() {
        Page<AnnonceResponseDTO> page = annonceService.rechercherAnnonces(
                PageRequest.of(0, 10), null, null, null, null, null, null, null, null, null, null, null, null, false, null
        );

        assertEquals(1, page.getTotalElements(), "Seule l'annonce active doit être retournée");
        assertTrue(page.getContent().stream().anyMatch(a -> a.getId().equals(activeAnnonce.getId())));
        assertFalse(page.getContent().stream().anyMatch(a -> a.getId().equals(deletedAnnonce.getId())), "L'annonce supprime=true doit être exclue");
    }
}
