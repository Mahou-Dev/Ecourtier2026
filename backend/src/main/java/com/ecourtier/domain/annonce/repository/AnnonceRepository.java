package com.ecourtier.domain.annonce.repository;

import com.ecourtier.domain.annonce.entity.AnnonceEntity;
import com.ecourtier.domain.annonce.entity.StatutVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnnonceRepository extends JpaRepository<AnnonceEntity, UUID>, JpaSpecificationExecutor<AnnonceEntity> {
    Optional<AnnonceEntity> findByIdAndSupprimeFalse(UUID id);
    Page<AnnonceEntity> findByStatutVerificationAndSupprimeFalse(StatutVerification statutVerification, Pageable pageable);
}
