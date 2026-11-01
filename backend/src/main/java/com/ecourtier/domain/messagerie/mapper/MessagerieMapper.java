package com.ecourtier.domain.messagerie.mapper;

import com.ecourtier.domain.messagerie.dto.ConversationResponseDTO;
import com.ecourtier.domain.messagerie.dto.MessageResponseDTO;
import com.ecourtier.domain.messagerie.entity.ConversationChatEntity;
import com.ecourtier.domain.messagerie.entity.MessageChatEntity;
import com.ecourtier.domain.utilisateur.mapper.UtilisateurMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagerieMapper {

    private final UtilisateurMapper utilisateurMapper;

    public MessageResponseDTO toMessageResponseDTO(MessageChatEntity entity) {
        if (entity == null) {
            return null;
        }
        return MessageResponseDTO.builder()
                .id(entity.getId())
                .conversationId(entity.getConversation() != null ? entity.getConversation().getId() : null)
                .expediteur(utilisateurMapper.toResponseDTO(entity.getExpediteur()))
                .contenu(entity.getContenu())
                .dateEnvoi(entity.getDateEnvoi())
                .lu(entity.isLu())
                .build();
    }

    public ConversationResponseDTO toConversationResponseDTO(ConversationChatEntity entity, MessageChatEntity dernierMessage, long nbMessagesNonLus) {
        if (entity == null) {
            return null;
        }
        return ConversationResponseDTO.builder()
                .id(entity.getId())
                .annonceId(entity.getAnnonce() != null ? entity.getAnnonce().getId() : null)
                .annonceTitre(entity.getAnnonce() != null ? entity.getAnnonce().getTitre() : null)
                .locataire(utilisateurMapper.toResponseDTO(entity.getLocataire()))
                .proprietaire(utilisateurMapper.toResponseDTO(entity.getProprietaire()))
                .dernierMessage(toMessageResponseDTO(dernierMessage))
                .nbMessagesNonLus(nbMessagesNonLus)
                .dateCreation(entity.getDateCreation())
                .build();
    }
}
