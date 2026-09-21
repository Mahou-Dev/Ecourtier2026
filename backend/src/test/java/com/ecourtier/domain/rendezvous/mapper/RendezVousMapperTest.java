package com.ecourtier.domain.rendezvous.mapper;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.rendezvous.dto.RendezVousResponseDTO;
import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import com.ecourtier.domain.utilisateur.entity.UtilisateurEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RendezVousMapperTest {

    private final RendezVousMapper mapper = new RendezVousMapper();

    @Test
    @DisplayName("Conversion de RendezVousEntity en RendezVousResponseDTO")
    void testToResponseDTO() {
        assertNull(mapper.toResponseDTO(null));

        UtilisateurEntity user = UtilisateurEntity.builder()
                .id(UUID.randomUUID())
                .prenom("Moussa")
                .nom("Diop")
                .email("moussa@test.sn")
                .build();

        AnnonceEntity annonce = AnnonceEntity.builder()
                .id(UUID.randomUUID())
                .titre("Villa Saly")
                .build();

        RendezVousEntity entity = RendezVousEntity.builder()
                .id(UUID.randomUUID())
                .annonce(annonce)
                .demandeur(user)
                .dateHeure(LocalDateTime.now())
                .statut(StatutRendezVous.CONFIRME)
                .rappelEnvoye(true)
                .motifAnnulation(null)
                .build();

        RendezVousResponseDTO dto = mapper.toResponseDTO(entity);

        assertNotNull(dto);
        assertEquals(entity.getId(), dto.getId());
        assertEquals("Villa Saly", dto.getAnnonceTitre());
        assertEquals("Moussa Diop", dto.getDemandeurNomComplet());
        assertEquals("moussa@test.sn", dto.getDemandeurEmail());
    }
}
