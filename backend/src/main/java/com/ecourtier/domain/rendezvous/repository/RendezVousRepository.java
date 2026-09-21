package com.ecourtier.domain.rendezvous.repository;

import com.ecourtier.domain.rendezvous.entity.RendezVousEntity;
import com.ecourtier.domain.rendezvous.entity.StatutRendezVous;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RendezVousRepository extends JpaRepository<RendezVousEntity, UUID> {

    List<RendezVousEntity> findByAnnonceIdOrderByDateHeureAsc(UUID annonceId);

    List<RendezVousEntity> findByDemandeurIdOrderByDateHeureDesc(UUID demandeurId);

    List<RendezVousEntity> findByAnnonceUtilisateurIdOrderByDateHeureDesc(UUID proprietaireId);

    List<RendezVousEntity> findByDemandeurIdOrAnnonceUtilisateurIdOrderByDateHeureDesc(UUID demandeurId, UUID proprietaireId);

    Optional<RendezVousEntity> findFirstByAnnonceIdAndDemandeurIdAndStatut(UUID annonceId, UUID demandeurId, StatutRendezVous statut);

    boolean existsByAnnonceIdAndDemandeurIdAndStatut(UUID annonceId, UUID demandeurId, StatutRendezVous statut);

    @Query("""
        SELECT r FROM RendezVousEntity r 
        WHERE r.annonce.id = :annonceId 
        AND r.statut = 'CONFIRME' 
        AND r.id <> :excludeRdvId 
        AND r.dateHeure BETWEEN :startSlot AND :endSlot
    """)
    List<RendezVousEntity> findConflictingConfirmedSlot(
            @Param("annonceId") UUID annonceId,
            @Param("excludeRdvId") UUID excludeRdvId,
            @Param("startSlot") LocalDateTime startSlot,
            @Param("endSlot") LocalDateTime endSlot
    );

    List<RendezVousEntity> findByStatutAndRappelEnvoyeFalseAndDateHeureBetween(
            StatutRendezVous statut,
            LocalDateTime startRange,
            LocalDateTime endRange
    );
}
