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
public class MessageResponseDTO {
    private UUID id;
    private UUID conversationId;
    private UtilisateurResponseDTO expediteur;
    private String contenu;
    private LocalDateTime dateEnvoi;
    private boolean lu;
}
