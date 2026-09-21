package com.ecourtier.domain.ia.repository;

import com.ecourtier.domain.ia.entity.MessageIAEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageIARepository extends JpaRepository<MessageIAEntity, UUID> {
    List<MessageIAEntity> findByConversationIdOrderByDateEnvoiAsc(UUID conversationId);
}
