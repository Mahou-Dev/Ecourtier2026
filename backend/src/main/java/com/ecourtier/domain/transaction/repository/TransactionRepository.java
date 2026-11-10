package com.ecourtier.domain.transaction.repository;

import com.ecourtier.domain.transaction.entity.StatutSequestre;
import com.ecourtier.domain.transaction.entity.TransactionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionEntity t WHERE t.id = :id")
    Optional<TransactionEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByAnnonceIdAndStatutSequestreIn(UUID annonceId, Collection<StatutSequestre> statuts);

    boolean existsByAnnonceIdAndDossierActifTrueAndStatutSequestreIn(UUID annonceId, Collection<StatutSequestre> statuts);

    List<TransactionEntity> findByAnnonceIdOrderByDateCreationDesc(UUID annonceId);

    List<TransactionEntity> findByLocataireIdOrProprietaireIdOrderByDateCreationDesc(UUID locataireId, UUID proprietaireId);

    Optional<TransactionEntity> findByAnnonceIdAndLocataireId(UUID annonceId, UUID locataireId);

    Page<TransactionEntity> findByStatutSequestre(StatutSequestre statutSequestre, Pageable pageable);
}
