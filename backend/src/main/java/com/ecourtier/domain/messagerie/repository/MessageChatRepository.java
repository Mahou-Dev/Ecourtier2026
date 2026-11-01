package com.ecourtier.domain.messagerie.repository;

import com.ecourtier.domain.messagerie.entity.MessageChatEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageChatRepository extends JpaRepository<MessageChatEntity, UUID> {

    Page<MessageChatEntity> findByConversationIdOrderByDateEnvoiAsc(UUID conversationId, Pageable pageable);

    List<MessageChatEntity> findByConversationIdOrderByDateEnvoiAsc(UUID conversationId);

    Optional<MessageChatEntity> findFirstByConversationIdOrderByDateEnvoiDesc(UUID conversationId);

    @Query("SELECT COUNT(m) FROM MessageChatEntity m WHERE m.conversation.id = :conversationId AND m.expediteur.id != :userId AND m.lu = false")
    long countUnreadMessagesForUserInConversation(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(m) FROM MessageChatEntity m WHERE (m.conversation.locataire.id = :userId OR m.conversation.proprietaire.id = :userId) AND m.expediteur.id != :userId AND m.lu = false")
    long countTotalUnreadMessagesForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE MessageChatEntity m SET m.lu = true WHERE m.conversation.id = :conversationId AND m.expediteur.id != :userId AND m.lu = false")
    int markMessagesAsRead(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
