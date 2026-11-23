package com.ecourtier.domain.ia.repository;

import com.ecourtier.domain.ia.entity.MessageIAEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageIARepository extends JpaRepository<MessageIAEntity, UUID> {

    @Query("SELECT m FROM MessageIAEntity m WHERE m.conversation.id = :conversationId ORDER BY m.ordre ASC, m.dateEnvoi ASC, m.id ASC")
    List<MessageIAEntity> findByConversationIdOrderByOrdreAscDateEnvoiAscIdAsc(@Param("conversationId") UUID conversationId);

    @Query("SELECT m FROM MessageIAEntity m WHERE m.conversation.id = :conversationId ORDER BY m.ordre ASC, m.dateEnvoi ASC, m.id ASC")
    List<MessageIAEntity> findByConversationIdOrderByDateEnvoiAsc(@Param("conversationId") UUID conversationId);

    @Query("SELECT COALESCE(MAX(m.ordre), 0) FROM MessageIAEntity m WHERE m.conversation.id = :conversationId")
    long findMaxOrdreByConversationId(@Param("conversationId") UUID conversationId);

    long countByConversationId(UUID conversationId);
}
