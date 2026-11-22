package com.ecourtier.domain.ia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationCreateResponseDTO {
    private UUID conversationId;
    private LocalDateTime dateCreation;
    /**
     * Jeton opaque de preuve de possession émis uniquement à la création d'une conversation anonyme.
     * Toujours null/absent pour une conversation authentifiée.
     */
    private String sessionToken;
}
