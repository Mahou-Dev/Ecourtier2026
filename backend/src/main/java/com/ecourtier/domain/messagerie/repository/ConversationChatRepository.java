package com.ecourtier.domain.messagerie.repository;

import com.ecourtier.domain.messagerie.entity.ConversationChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationChatRepository extends JpaRepository<ConversationChatEntity, UUID> {

    Optional<ConversationChatEntity> findByAnnonceIdAndLocataireId(UUID annonceId, UUID locataireId);

    @Query("SELECT c FROM ConversationChatEntity c WHERE c.locataire.id = :userId OR c.proprietaire.id = :userId ORDER BY c.dateCreation DESC")
    List<ConversationChatEntity> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ConversationChatEntity c WHERE c.id = :id AND (c.locataire.email = :email OR c.proprietaire.email = :email)")
    boolean isParticipant(@Param("id") UUID id, @Param("email") String email);
}
