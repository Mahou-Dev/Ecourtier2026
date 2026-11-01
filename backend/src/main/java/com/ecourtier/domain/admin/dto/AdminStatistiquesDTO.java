package com.ecourtier.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatistiquesDTO {
    private String periode;
    private UtilisateursStatsDTO utilisateurs;
    private AnnoncesStatsDTO annonces;
    private ActiviteCommercialeStatsDTO activiteCommerciale;
    private ModerationStatsDTO moderation;
    private AssistantIAStatsDTO assistantIA;
}
