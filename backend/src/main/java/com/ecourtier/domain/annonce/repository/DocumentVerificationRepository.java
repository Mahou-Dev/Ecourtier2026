package com.ecourtier.domain.annonce.repository;

import com.ecourtier.domain.annonce.entity.DocumentVerificationEntity;
import com.ecourtier.domain.annonce.entity.StatutDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentVerificationRepository extends JpaRepository<DocumentVerificationEntity, UUID> {
    List<DocumentVerificationEntity> findByAnnonceId(UUID annonceId);
    List<DocumentVerificationEntity> findByStatut(StatutDocument statut);
}
