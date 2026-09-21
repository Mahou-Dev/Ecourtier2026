package com.ecourtier.domain.ia.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationHistoryResponseDTO {
    private UUID conversationId;
    private LocalDateTime dateCreation;
    private List<MessageItemDTO> messages;
}
