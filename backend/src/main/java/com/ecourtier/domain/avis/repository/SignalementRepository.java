package com.ecourtier.domain.avis.repository;

import com.ecourtier.domain.avis.entity.SignalementEntity;
import com.ecourtier.domain.avis.entity.StatutSignalement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SignalementRepository extends JpaRepository<SignalementEntity, UUID> {

    Page<SignalementEntity> findByStatut(StatutSignalement statut, Pageable pageable);

    Page<SignalementEntity> findAll(Pageable pageable);
}
