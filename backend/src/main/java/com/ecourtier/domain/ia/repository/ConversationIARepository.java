package com.ecourtier.domain.ia.repository;

import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationIARepository extends JpaRepository<ConversationIAEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ConversationIAEntity c WHERE c.id = :id")
    Optional<ConversationIAEntity> findByIdForUpdate(@Param("id") UUID id);
}
