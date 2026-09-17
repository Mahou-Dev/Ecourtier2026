package com.ecourtier.domain.annonce.repository;

import com.ecourtier.domain.annonce.entity.PhotoAnnonceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PhotoAnnonceRepository extends JpaRepository<PhotoAnnonceEntity, UUID> {
    List<PhotoAnnonceEntity> findByAnnonceIdOrderByOrdreAsc(UUID annonceId);
    long countByAnnonceId(UUID annonceId);
}
