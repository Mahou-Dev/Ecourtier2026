package com.ecourtier.domain.transaction.repository;

import com.ecourtier.domain.transaction.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByAnnonceIdOrderByDateCreationDesc(UUID annonceId);

    List<TransactionEntity> findByLocataireIdOrProprietaireIdOrderByDateCreationDesc(UUID locataireId, UUID proprietaireId);

    Optional<TransactionEntity> findByAnnonceIdAndLocataireId(UUID annonceId, UUID locataireId);
}
