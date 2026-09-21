package com.ecourtier.domain.ia.repository;

import com.ecourtier.domain.ia.entity.ConversationIAEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConversationIARepository extends JpaRepository<ConversationIAEntity, UUID> {
}
