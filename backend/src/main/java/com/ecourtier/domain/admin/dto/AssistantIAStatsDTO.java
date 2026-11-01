package com.ecourtier.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssistantIAStatsDTO {
    private long nombreConversations;
    private Double tauxConversationsShortlistNonVide;
}
