package com.ecourtier.domain.annonce.repository;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnnonceRepository extends JpaRepository<AnnonceEntity, UUID>, JpaSpecificationExecutor<AnnonceEntity> {

    @Override
    @EntityGraph(attributePaths = {"utilisateur"})
    Page<AnnonceEntity> findAll(Specification<AnnonceEntity> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"utilisateur"})
    Optional<AnnonceEntity> findByIdAndSupprimeFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AnnonceEntity a WHERE a.id = :id AND a.supprime = false")
    Optional<AnnonceEntity> findByIdAndSupprimeFalseForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"utilisateur"})
    Page<AnnonceEntity> findByStatutVerificationAndSupprimeFalse(StatutVerification statutVerification, Pageable pageable);

    @Query("SELECT DISTINCT a.quartier FROM AnnonceEntity a WHERE a.supprime = false AND a.statutPublication = com.ecourtier.domain.annonce.entity.StatutPublication.DISPONIBLE AND a.quartier IS NOT NULL AND TRIM(a.quartier) != '' ORDER BY a.quartier ASC")
    java.util.List<String> findDistinctQuartiers();
}
