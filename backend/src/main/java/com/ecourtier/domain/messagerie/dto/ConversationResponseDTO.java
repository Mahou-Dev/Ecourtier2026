package com.ecourtier.domain.messagerie.dto;

import com.ecourtier.domain.utilisateur.dto.UtilisateurResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponseDTO {
    private UUID id;
    private UUID annonceId;
    private String annonceTitre;
    private UtilisateurResponseDTO locataire;
    private UtilisateurResponseDTO proprietaire;
    private MessageResponseDTO dernierMessage;
    private long nbMessagesNonLus;
    private LocalDateTime dateCreation;
}
